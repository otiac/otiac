OTIAC_GUI {
	classvar awesomeFont = "Baskerville";

	var <window, <setupWindow;  // OTIAC main window
	var <setupFile = nil;       // JSON setup file
	var booterState = 0;        // State of BOOT button

	var <knobGUI;               // Array of GUI knobs
	var <buttonGUI;             // Array of GUI buttons
	var <activeButton;          // Array of state buttons

	var <server;                // SC server

	var <analIn, <analTrans, <analOutLeft, <analOutRight, <analOtiac; // all the FreqScope buses
	var <midiKnobs, <midiButtons;      //MIDI knobs and buttons

	var outTransducer, outOtiac;       // input/output bus
	var analFreqWidth = 340, analFreqHeight = 200; // Hardcoded FreqView dimensions

	// All the JSON fields
	var <knobsField, <toggleField, <holdField, <outLField, <outRField, <transducerField, <contactMicField, <outMicField, <reson1Field, <reson2Field, <reson3Field, <choiceInDevice, <choiceOutDevice;

	// The actual varuables used in the GUI
	var <inDevNum = 0, <outDevNum = 0, <inDev, <outDev, <knobs, <toggle, <hold, <outL = 0, <outR = 1, <transducer = 0, <contactMic = 0, <outMic = 0, <reson1 = 0, <reson2 = 0, <reson3 = 0;


	*new { |jsonSetupFile, server|
		var proceedFunc;

		// Create a function that will create the instance
		proceedFunc = {
			super.new.init(jsonSetupFile, server ? Server.default);
		};

		// Check dependencies, pass the proceed function
		if(this.dependencyCheck(proceedFunc).not, { ^nil });

		^proceedFunc.value;
	}


	*folderImages {
		^Platform.userExtensionDir +/+ "OTIAC/otiac_gui_images";
	}

	// Check if dependencies are available
	*dependencyCheck {|proceedFunc|
		var missingDeps = List.new;

		if (JSONlib.isNil) {
			missingDeps.add("JSONlib");
		};

		if (SOMTrain.isNil or: { SOMRd.isNil }) {
			missingDeps.add("SOM");
		};

		if (FluidLoudness.isNil or: { FluidSpectralShape.isNil } or: { FluidMFCC.isNil } or: { FluidPitch.isNil }) {
			missingDeps.add("FluCoMa");
		};

		if (FluidNoveltyFeature.isNil) {
			missingDeps.add("FluidNoveltyFeature");
		};

		if (FactorOracle.isNil or: { FactorOracleView.isNil }) {
			missingDeps.add("FactorOracle");
		};

		if (OTIAC.isNil) {
			missingDeps.add("OTIAC");
		};

		if (missingDeps.size > 0) {
			this.showDependencyError(missingDeps, proceedFunc);
			^false;
		};

		^true;
	}


	// Show error dialog and post to post window (CLASS METHOD)
	*showDependencyError { |missingDeps, proceedFunc|
		var errorMsg = "%".format(missingDeps.join(", "));
		var listDeps = "";
		var thisClass = this;

		missingDeps.do { |dep|
			case
			{ dep == "JSONlib" } {
				listDeps = listDeps ++ "→ JSONlib: Quarks.install(\"JSONlib\")\n";
			}
			{ dep == "SOM" } {
				listDeps = listDeps ++ "→ SOM: Install from https://supercollider.github.io/sc3-plugins/ \n";
			}
			{ dep == "FluCoMa" } {
				listDeps = listDeps ++ "→ FluCoMa: Install from https://www.flucoma.org/\n";
			}
			{ dep == "FluidNoveltyFeature" } {
				listDeps = listDeps ++ "→ FluidNoveltyFeature: update FluCoMa from https://www.flucoma.org/\n";
			}
			{ dep == "FactorOracle" } {
				listDeps = listDeps ++ "→ Install FactorOracle/\n";
			}
			{ dep == "OTIAC" } {
				listDeps = listDeps ++ "→ Install OTIAC/\n";
			};
		};

		errorMsg.error;
		listDeps.postln;

		// Show dialog window
		{
			var dialog, flowDialog, image, resize;
			dialog = Window("Missing Dependencies", Rect(400, 300, 500, 500), resizable:false)
			.front
			.background_(Color.new255(230, 230, 230));
			image = Image.open(thisClass.folderImages+/+"medieval_glasses.png");
			resize = 0.36;
			dialog.drawFunc_({
				Pen.scale(resize, resize);
				Pen.drawImage(Point(resize.linlin(0.25, 0.5, 1000, 0), resize.linlin(0.25, 0.5, 1000, 0)), image, operation: 'sourceOver', opacity: 1);
			});
			flowDialog = dialog.addFlowLayout;

			StaticText(dialog, 360@30)
			.font_(Font(awesomeFont, 20))
			.stringColor_(Color.white).background_(Color.red)
			.string_("⚠︎ Missing dependencies: ⚠︎")
			.align_(\left);
			flowDialog.nextLine;

			StaticText(dialog, 360@90)
			.font_(Font(awesomeFont, 20))
			.stringColor_(Color.black)
			.string_(errorMsg)
			.align_(\left);
			flowDialog.nextLine;

			StaticText(dialog, 360@30)
			.font_(Font(awesomeFont, 18))
			.string_("Please install the followings:")
			.stringColor_(Color.white).background_(Color.gray)
			.align_(\left);
			flowDialog.nextLine;

			StaticText(dialog, 360@180)
			.font_(Font(awesomeFont, 15))
			.string_(listDeps)
			.align_(\left);
			flowDialog.nextLine;

			// Buttons
			Button(dialog, 180@30)
			.font_(Font(awesomeFont, 18))
			.states_([["OK"]])
			.action_({ dialog.close });
			flowDialog.nextLine;

			Button(dialog, 180@60)
			.font_(Font(awesomeFont, 18))
			.states_([["Don't sweat it,\n bring me to the patch"]])
			.action_({
				dialog.close;
				proceedFunc.value; // Create the GUI anyway
			});
		}.defer;
	}

	openSetupWindow {
		var winWidth, winHeight, flowSetupWin, resize = 0.93, image;
		var booter; // the booting button

		image = Image.open(this.class.folderImages+/+"lion_devouring_sun.jpg");
		winWidth = resize*image.width;
		winHeight = resize*image.height;
		setupWindow = Window("OTIAC Setup", Rect(600, 200, winWidth, winHeight), resizable:false ).front.background_(Color.new255(230, 230, 230));

		//---------------------------- ACTUAL GUI ELEMENTS ----------------------------//
		setupWindow.drawFunc_({
			Pen.scale(resize, resize);
			Pen.drawImage(Point(0, 0), image, operation: 'sourceOver', opacity: 1);
		});
		flowSetupWin = setupWindow.addFlowLayout;

		// Choice input device
		StaticText(setupWindow, 200@25).string_("Choose input device:").font_(Font(awesomeFont, 15)).stringColor_(Color.white).align_(\left).background_(Color.gray);
		choiceInDevice = PopUpMenu(setupWindow, Rect(10, 10, 200, 25))
		.font_(Font(awesomeFont, 15))
		.stringColor_(Color.white)
		.background_(Color.gray)
		.items_(ServerOptions.inDevices)
		.valueAction_(inDevNum);
		choiceInDevice.action = { arg menu; inDev = menu.item; inDevNum = menu.value; ("Selected input device: "++inDev).postlnSpecial; Server.default.options.inDevice_(inDev);};
		flowSetupWin.nextLine;

		// Choice output device
		StaticText(setupWindow, 200@25).string_("Choose output device:").font_(Font(awesomeFont, 15)).stringColor_(Color.white).align_(\left).background_(Color.gray);
		choiceOutDevice = PopUpMenu(setupWindow, Rect(10, 10, 200, 25))
		.font_(Font(awesomeFont, 15)).stringColor_(Color.white)
		.background_(Color.gray)
		.items_(ServerOptions.outDevices)
		.valueAction_(outDevNum);
		choiceOutDevice.action = { arg menu; outDev = menu.item; outDevNum = menu.value; ("Selected output device: "++outDev).postlnSpecial; Server.default.options.outDevice_(outDev);};
		flowSetupWin.nextLine;

		// BOOTING BUTTON
		booter = Button(setupWindow, 400@40)
		.font_(Font(awesomeFont, 28))
		.states_([["Boot server", Color.black, Color.red(1, 0.5)], ["Quit server", Color.black, Color.green(1, 0.5)]]);
		booter.value = booterState;
		booter.action = { arg view;
			if(view.value == 1, {
				server.options.memSize = 2.pow(21);
				server.options.blockSize = 64;
				server.options.maxNodes = 8000;
				server.options.numWireBufs = 256;
				server.options.numBuffers = 2048;
				server.options.sampleRate = 48000;
				server.options.numOutputBusChannels = 10;
				server.options.numInputBusChannels = 10;

				server.waitForBoot({
					booterState = 1;
					//(thisProcess.nowExecutingPath.dirname+/+"OTIAC_synths.scd").load;
					OTIAC_synths.load(server);
					server.sync; //
					outTransducer = ~outGuitar;
					outMic = 3;
					outOtiac = ~monitorBus;
					this.updateFreqScopeBuses;
					"Server booted at sample rate = 48kHz".postlnSpecial(~otiac_logWindow);
				});

			});
			if(view.value == 0, {
				server.quit;
				booterState = 0;
		}); };
		flowSetupWin.nextLine;
		//
		//
		//
		flowSetupWin.shift(0, 40);
		//
		StaticText(setupWindow, 520@25).string_("Adjust the following fields based on your MIDI controller (e.g. 5, 6, 7, etc.)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\left).background_(Color.gray);
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 280@25).string_("MIDI knobs (9)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		knobsField = TextField(setupWindow, 240@25).font_(Font(awesomeFont, 16))
		.string_(knobs.asString)
		.action_({arg field;
			knobs = field.value;
			midiKnobs = knobs.stringToArray;
			this.updateMIDIdefs;
			("MIDI knobs: "++field.value).postlnSpecial; });
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 280@25).string_("MIDI buttons for FO (3)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		toggleField = TextField(setupWindow, 130@25).font_(Font(awesomeFont, 16))
		.string_(toggle.asString)
		.action_({arg field;
			toggle = field.value;
			midiButtons = toggle.stringToArray++hold.stringToArray;
			this.updateMIDIdefs;
			("MIDI toggle buttons: "++field.value).postlnSpecial; });
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 280@25).string_("MIDI buttons for Constraints (5)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		holdField = TextField(setupWindow, 130@25).font_(Font(awesomeFont, 16))
		.string_(hold.asString)
		.action_({arg field;
			hold = field.value;
			midiButtons = toggle.stringToArray++hold.stringToArray;
			this.updateMIDIdefs;
			("MIDI hold buttons: "++field.value).postlnSpecial; });
		flowSetupWin.nextLine;
		//
		//
		//
		flowSetupWin.shift(0, 40);
		StaticText(setupWindow, 340@25).string_("Adjust the following fields based on your routing.").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\left).background_(Color.gray);
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Left Speaker ch").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		outLField = NumberBox(setupWindow, 80@25).value_(outL).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			outL = numb.value;
			analOutLeft.inBus_(outL);
			("Left speaker channel: "++numb.value.asInteger).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Right Speaker ch").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		outRField = NumberBox(setupWindow, 80@25).value_(outR).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			outR = numb.value;
			analOutRight.inBus_(outR);
			("Right speaker channel: "++numb.value.asInteger).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Transducer ch").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		transducerField = NumberBox(setupWindow, 80@25).value_(transducer).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			transducer = numb.value;
			("Transducer channel: "++numb.value.asInteger).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Contact mic ch").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		contactMicField = NumberBox(setupWindow, 80@25).value_(contactMic).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			contactMic = numb.value;
			("Contact mic channel: "++numb.value.asInteger).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Mic monitoring ch ").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		outMicField = NumberBox(setupWindow, 80@25).value_(outMic).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			outMic = numb.value;
			analIn.inBus_(outMic);
			("Out mic channel: "++numb.value.asInteger).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		//
		//
		flowSetupWin.shift(0, 40);
		StaticText(setupWindow, 340@25).string_("Adjust the following fields based on your resonances.").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\left).background_(Color.gray);
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Resonance 1 (Hz)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		reson1Field = NumberBox(setupWindow, 80@25).value_(reson1).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			reson1 = numb.value;
			("Resonance 1 value: "++numb.value).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Resonance 2 (Hz)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		reson2Field = NumberBox(setupWindow, 80@25).value_(reson2).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			reson2 = numb.value;
			("Resonance 2 value: "++numb.value).postlnSpecial; })
		.focus;
		flowSetupWin.nextLine;
		//
		StaticText(setupWindow, 200@25).string_("Resonance 3 (Hz)").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		reson3Field = NumberBox(setupWindow, 80@25).value_(reson3).font_(Font(awesomeFont, 16))
		.action_({arg numb;
			reson3 = numb.value;
			("Resonance 3 value: "++numb.value).postlnSpecial; })
		.focus;
		//
		flowSetupWin.shift(250, -10);
		Button(setupWindow, 110@35).font_(Font(awesomeFont, 18)).states_([["Save setup", Color.black, Color.new255(0, 200, 0)]])
		.action_({
			Dialog.savePanel({ arg path; this.saveJSON(path);});
		});
		Button(setupWindow, 110@35).font_(Font(awesomeFont, 18)).states_([["Load setup", Color.black, Color.new255(0, 200, 255)]])
		.action_({
			Dialog.openPanel({ arg path; this.loadJSON(path);});
		});

		if(setupFile.notNil, {this.loadJSON(setupFile)});

	}

	saveJSON {arg jsonFile;
		var writefile, jsonString;
		jsonString = JSONlib.convertToJSON(this.convertSetupToEvent);

		writefile = File(jsonFile++".json", "w");
		writefile.write(jsonString);
		writefile.close;
		"JSON file saved.".postlnSpecial(~otiac_logWindow);
	}

	convertSetupToEvent {
		var event;
		event = (
			\inputDevice: choiceInDevice.item,
			\outputDevice: choiceOutDevice.item,
			\midiKnobs: knobsField.value,
			\toggle: toggleField.value,
			\hold: holdField.value,
			\outL: outLField.value,
			\outR: outRField.value,
			\contactMic: contactMicField.value,
			\outMic: outMicField.value,
			\transducer: transducerField.value,
			\reson1: reson1Field.value,
			\reson2: reson2Field.value,
			\reson3: reson3Field.value
		);
		^event;
	}

	getJSONFile {
		setupFile.postln;
	}

	loadJSON {arg jsonFile;
		var jsonString;
		jsonString = JSONlib.parseFile(jsonFile);

		inDev = jsonString[\inputDevice];
		choiceInDevice.valueAction_(choiceInDevice.items.find([inDev]) ? 0);

		outDev = jsonString[\outputDevice];
		choiceOutDevice.valueAction_(choiceOutDevice.items.find([outDev]) ? 0);

		knobs = jsonString[\midiKnobs];
		knobsField.value = knobs;

		toggle = jsonString[\toggle];
		toggleField.value = toggle;

		hold = jsonString[\hold];
		holdField.value = hold;

		outL = jsonString[\outL];
		outLField.value = outL;

		outR = jsonString[\outR];
		outRField.value = outR;

		contactMic = jsonString[\contactMic];
		contactMicField.value = contactMic;

		outMic = jsonString[\outMic];
		outMicField.value = outMic;

		transducer = jsonString[\transducer];
		transducerField.value = transducer;

		reson1 = jsonString[\reson1];
		reson1Field.value = reson1;

		reson2 = jsonString[\reson2];
		reson2Field.value = reson2;

		reson3 = jsonString[\reson3];
		reson3Field.value = reson3;

		"JSON file loaded.".postlnSpecial(~otiac_logWindow);
		this.updateFreqScopeBuses;
		this.updateMIDIdefs;
	}

	loadJSONonlyMIDI {arg jsonFile;
		var jsonString;
		var loadedKnobs, loadedButtons;
		jsonString = JSONlib.parseFile(jsonFile);

		knobs = jsonString[\midiKnobs];
		toggle = jsonString[\toggle];
		hold = jsonString[\hold];

		loadedKnobs = knobs.stringToArray;
		loadedButtons = toggle.stringToArray++hold.stringToArray;

		"JSON file loaded.".postlnSpecial(~otiac_logWindow);
		this.updateMIDIdefs(loadedKnobs, loadedButtons) ;
	}

	updateFreqScopeBuses {
		analIn.inBus_(outMic);
		analTrans.inBus_(outTransducer);
		analOutLeft.inBus_(outL);
		analOutRight.inBus_(outR);
		analOtiac.inBus_(outOtiac);
		"FreqScope buses updated.".postlnSpecial(~otiac_logWindow);
	}

	init {|jsonSetupFile, serverAudio|
		var image1, image2, flowWin, initializeButton, startButton, timerMins, timerSecs;
		var windowWidth = Window.screenBounds.width;
		knobGUI = Array.fill(9, {0});
		buttonGUI = Array.fill(8, {0});
		activeButton = Array.fill(8, {0});

		setupFile = jsonSetupFile;

		midiKnobs = 0!9;
		midiButtons = 0!8;
		server = serverAudio;

		//---------------------------- ACTUAL GUI ELEMENTS ----------------------------//
		window = Window("OTIAC GUI", Rect(100, 200, windowWidth, 850)).front.background_(Color.new255(230, 230, 230));
		image1 = Image.open(this.class.folderImages+/+"corner_topRight.png");
		image2 = Image.open(this.class.folderImages+/+"corner_bottomRight.png");

		window.drawFunc_({
			Pen.push;
			Pen.scale(0.62, 0.62);
			Pen.drawImage(Point(windowWidth-400, 0), image1, operation: 'sourceOver', opacity: 1);
			Pen.pop; //not necessary now, but useful if I want to draw images with two different scales

			Pen.push;
			Pen.scale(0.62, 0.62);
			Pen.drawImage(Point(windowWidth-400, 600), image2, operation: 'sourceOver', opacity: 1);
			Pen.pop;
		});
		flowWin = window.addFlowLayout;

		// Setup button
		Button(window, analFreqWidth/3-1@35)
		.font_(Font(awesomeFont, 18))
		.states_([["(1) Setup", Color.black, Color.gray(0.7)]])
		.action_({ this.openSetupWindow });

		// Calibration button
		Button(window, analFreqWidth/3@35)
		.font_(Font(awesomeFont, 16))
		.states_([["(2) Calibration", Color.black, Color.gray(0.7)], ["Stop Calibration", Color.black, Color.red]])
		.action_({ |view|
			if(view.value == 1, {
				~oscCheckariell = OSCdef(\ampCheck, { |msg|
					if(msg[3] == 1, {"GOOD RANGE".postlnSpecial(~otiac_logWindow);});
					if(msg[4] == 1, {"No good -- Increase ^^^".postlnSpecial(~otiac_logWindow)});
					if(msg[5] == 1, {"No good -- Decrease vvv".postlnSpecial(~otiac_logWindow);});
				}, '/ampCheck');
				~synthCheckariell = Synth(\checkariell, [\inBus, contactMic, \outBus, transducer]);
			}, {
				~oscCheckariell.free;
				~synthCheckariell.free;
			});
		});

		// Test basic feedback
		Button(window, analFreqWidth/3-1@35)
		.font_(Font(awesomeFont, 18))
		.states_([["(2b) Fb test", Color.black, Color.gray(0.7)], ["Stop Fb test", Color.black, Color.red]])
		.action_({ |view|
			if(view.value == 1, {
				~theMostBasicFeedback = Synth(\theMostBasicFeedback, [\busIn, contactMic, \busOut, transducer]);
			}, {
				~theMostBasicFeedback.release;
			});
		});

		// Help Button
		Button(window, analFreqWidth/3-1@35)
		.font_(Font(awesomeFont, 18))
		.states_([["Help", Color.black, Color.gray(0.7)]])
		.action_({ HelpBrowser.new(newWin: true).goTo(SCDoc.findHelpFile("Classes/OTIAC_GUI")); });

		// Title
		StaticText(window, windowWidth-480@35).string_("OTIAC").font_(Font(awesomeFont, 44, bold:true)).stringColor_(Color.black).align_(\right);
		flowWin.nextLine;

		//
		initializeButton = Button(window, (analFreqWidth/2)@35).font_(Font(awesomeFont, 18));
		initializeButton.states = [["(3) Initialize SOM", Color.black, Color.new255(0, 200, 0)], ["free SOM", Color.white, Color.red]];
		initializeButton.action = {|view|
			if (view.value == 1) {
				~oracle = OTIAC.new(
					bufferMinutes: 10,      // 10 minute circular buffer
					inputAudioBus: ~busOTIAC,      // Audio input bus
					somNetSize: 10,         // 10x10 SOM grid (100 clusters)
					outputTransducer: ~outSegments,
					outputLeft:~outRoom,
					monitoringBusOTIAC: ~monitorBus,
				);
			} {
				~oracle.free;
			};
		};

		startButton = Button(window, (analFreqWidth/2)@35).font_(Font(awesomeFont, 16));
		startButton.states = [["(4) Start synths and timer", Color.black, Color.new255(0, 200, 0)], ["Stop everything", Color.white, Color.red]];
		startButton.action = {|view|
			if (view.value == 1) {
				~timerSynth = Synth(\timerSynth);
				~otiacSynths[0] = Synth(\otiac, [\busIn, contactMic, \busOut, ~outGuitar, \busOTIAC, ~busOTIAC, \outMic, outMic, \freq, reson1, \freqShift, 0, \delay, 0.002, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0, \levelOut, ~levelReson1.asMap]);
				~otiacSynths[1] = Synth(\otiac, [\busIn, contactMic, \busOut, ~outGuitar, \busOTIAC, ~busOTIAC, \outMic, outMic, \freq, reson2, \freqShift, 0, \delay, 0.0007, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0, \levelOut, ~levelReson2.asMap]);
				~otiacSynths[2] = Synth(\otiac, [\busIn, contactMic, \busOut, ~outGuitar, \busOTIAC, ~busOTIAC, \outMic, outMic, \freq, reson3*3, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0, \levelOut, ~levelReson3.asMap]);
				~otiacSynths[3] = Synth(\otiacAdapt, [\busIn, contactMic, \busOut, ~outGuitar, \busOTIAC, ~busOTIAC, \outMic, outMic, \levelOut, ~levelAdaptive.asMap]);
				~mixer = Synth(\otiac_mixer, [\outMic, outMic, \levelTransducer, 1, \outTransducer, transducer, \outL, outL, \levelSegments, ~levelSegments.asMap, \levelRoom, ~levelRoom.asMap], addAction: \addToTail);

			} {
				~timerSynth.free; ~otiacSynths[0].free; ~otiacSynths[1].free; ~otiacSynths[2].free; ~otiacSynths[3].free; ~mixer.release;
			};
		};
		StaticText(window, windowWidth-360@30).string_("Claudio Panariello")
		.font_(Font(awesomeFont, 28))
		.stringColor_(Color.clear)
		.align_(\right);
		flowWin.nextLine;
		//
		StaticText(window, analFreqWidth@25).string_("Input Contact Mic")
		.font_(Font(awesomeFont, 16))
		.stringColor_(Color.white)
		.align_(\center)
		.background_(Color.gray);
		//
		StaticText(window, windowWidth-360@30).string_("2025")
		.font_(Font(awesomeFont, 20)).
		stringColor_(Color.clear)
		.align_(\right);
		flowWin.nextLine;
		//
		analIn = CustomFreqScopeView(window, Rect(0, 0, analFreqWidth, analFreqHeight))
		.freqMode_(1)
		.inBus_(outMic)
		.active_(true);
		flowWin.shift(0, -20);
		ServerMeterView.new(server, window, 0@0, 9, 6); //server meter 9 in, 6 out
		//
		StaticText(window, (analFreqWidth/2)@25).string_("Timer").font_(Font(awesomeFont, 16)).stringColor_(Color.white).align_(\center).background_(Color.gray);
		flowWin.nextLine;

		flowWin.shift(708, -200);
		timerMins = NumberBox(window, Rect(250, 25, 60, 30)).font_(Font(awesomeFont, 25)).align_(\right);
		StaticText(window, Rect(260, 25, 7, 28)).string_(":").font_(Font(awesomeFont, 25)).stringColor_(Color.black);
		timerSecs = NumberBox(window, Rect(275, 25, 60, 30)).font_(Font(awesomeFont, 25));
		OSCFunc({arg msg;{timerMins.value_(msg[3].value)}.defer}, '/timerMinutes');
		OSCFunc({arg msg;{timerSecs.value_(msg[3].value)}.defer}, '/timerSeconds');

		// First LOG WINDOW more or less like a post window
		flowWin.shift(295, 0);
		~otiac_logWindow = TextView(window.asView, (windowWidth-1160)@300)
		.font_(Font(awesomeFont, 14))
		.stringColor_(Color.black)
		.background_(Color.new255(230, 230, 230))
		.editable_(false)
		.hasVerticalScroller_(true);
		flowWin.nextLine;

		flowWin.shift(710, -250);
		Button(window, 65@60).font_(Font(awesomeFont, 16)).states_([["Draw\n clusters", Color.black, Color.gray(0.7)]]).action_({~oracle.drawClusterDistribution});
		Button(window, 65@60).font_(Font(awesomeFont, 16)).states_([["Draw\n FO", Color.black, Color.gray(0.7)]]).action_({~oracle.drawOracle});

		flowWin.nextLine;

		flowWin.shift(0, 120);
		StaticText(window, analFreqWidth@25).string_("Out Guitar Transducer").font_(Font(awesomeFont, 16)).stringColor_(Color.black).align_(\center).background_(Color.white);
		StaticText(window, analFreqWidth@25).string_("Out Speaker L").font_(Font(awesomeFont, 16)).stringColor_(Color.black).align_(\center).background_(Color.new255(100, 100, 255));
		StaticText(window, analFreqWidth@25).string_("Out Speaker R").font_(Font(awesomeFont, 16)).stringColor_(Color.black).align_(\center).background_(Color.new255(255, 100, 100));
		flowWin.nextLine;
		analTrans = CustomFreqScopeView(window, Rect(0, 100, analFreqWidth, analFreqHeight))
		.freqMode_(1)
		.inBus_(outTransducer)
		.active_(true);

		analOutLeft = CustomFreqScopeView(window, Rect(0, 100, analFreqWidth, analFreqHeight))
		.freqMode_(1)
		.inBus_(outL)
		.active_(true);
		//flowWin.shift(-250, 0);
		analOutRight = CustomFreqScopeView(window, Rect(0, 100, analFreqWidth, analFreqHeight))
		.freqMode_(1)
		.inBus_(outR)
		.active_(true);

		// Second LOG WINDOW: also sort of post window but not for real time
		flowWin.shift(110, 50);
		~otiac_logWindowTwo = TextView(window.asView, (windowWidth-1160)@300)
		.font_(Font(awesomeFont, 14))
		.stringColor_(Color.black)
		.background_(Color.new255(230, 230, 230))
		.editable_(false)
		.hasVerticalScroller_(true);
		flowWin.nextLine;

		//
		flowWin.shift(0, -150);
		StaticText(window, analFreqWidth@25).string_("Out OTIAC (only monitoring)").font_(Font(awesomeFont, 16)).stringColor_(Color.black).align_(\center).background_(Color.new255(200, 0, 100));
		flowWin.nextLine;
		//
		analOtiac = CustomFreqScopeView(window, Rect(0, 100, analFreqWidth, analFreqHeight))
		.freqMode_(1)
		.inBus_(outOtiac)
		.active_(true);

		//---------------------------- MIDI CONTROLS ----------------------------//
		ControlSpec.specs[\levelReson1] = ControlSpec(0, 1.0, 'lin', 0.001, 0); //minval, maxval, warp: 'lin', step, default
		ControlSpec.specs[\levelReson2] = ControlSpec(0, 1.0, 'lin', 0.001, 0);
		ControlSpec.specs[\levelReson3] = ControlSpec(0, 1.0, 'lin', 0.001, 0);
		ControlSpec.specs[\levelAdaptive] = ControlSpec(0, 1.0, 'lin', 0.001, 0);
		ControlSpec.specs[\FO_interval] = ControlSpec(0.1, 7.0, 'lin', 0.1, 1);
		ControlSpec.specs[\FO_length] = ControlSpec(-1, 24, 'lin', 1, 14);
		ControlSpec.specs[\FO_continuity] = ControlSpec(0, 1.0, 'lin', 0.001, 0);
		ControlSpec.specs[\levelSegments] = ControlSpec(0, 1.0, 'lin', 0.001, 0);
		ControlSpec.specs[\levelRoom] = ControlSpec(0, 1.0, 'lin', 0.001, 0);

		~levelReson1 = Bus.control(server, 1).set(0);
		~levelReson2 = Bus.control(server, 1).set(0);
		~levelReson3 = Bus.control(server, 1).set(0);
		~levelAdaptive = Bus.control(server, 1).set(0);
		~fo_interval = Bus.control(server, 1).set(0);
		~fo_length = Bus.control(server, 1).set(14);
		~fo_continuity = Bus.control(server, 1).set(0);
		~levelSegments = Bus.control(server, 1).set(0);
		~levelRoom = Bus.control(server, 1).set(0);

		MIDIIn.connectAll;

		MIDIdef.cc(\levelReson1, {|v| var value = ControlSpec.specs[\levelReson1].map(v/127);
			("levelReson1: "++value).postln;
			~levelReson1.set(value);
			{knobGUI[0].value_(value)}.defer
		}, midiKnobs[0]).permanent_(true);
		MIDIdef.cc(\levelReson2, {|v| var value = ControlSpec.specs[\levelReson2].map(v/127);
			("levelReson2: "++value).postln;
			~levelReson2.set(value);
			{knobGUI[1].value_(value)}.defer
		}, midiKnobs[1]).permanent_(true);
		MIDIdef.cc(\levelReson3, {|v| var value = ControlSpec.specs[\levelReson3].map(v/127);
			("levelReson3: "++value).postln;
			~levelReson3.set(value);
			{knobGUI[2].value_(value)}.defer
		}, midiKnobs[2]).permanent_(true);
		MIDIdef.cc(\levelAdaptive, {|v| var value = ControlSpec.specs[\levelAdaptive].map(v/127);
			("levelAdaptive: "++value).postln;
			~levelAdaptive.set(value);
			{knobGUI[3].value_(value)}.defer
		}, midiKnobs[3]).permanent_(true);
		MIDIdef.cc(\FO_interval, {|v| var value = ControlSpec.specs[\FO_interval].map(v/127);
			("FO_interval: "++value).postln;
			~fo_interval.set(value); ~oracle.setGenerationInterval(value);
			{knobGUI[4].value_(value)}.defer
		}, midiKnobs[4]).permanent_(true);
		MIDIdef.cc(\FO_length, {|v| var value = ControlSpec.specs[\FO_length].map(v/127);
			("FO_length: "++value).postln;
			~fo_length.set(value);
			if(value == 1.neg, {~oracle.setOracleLength(nil)}, {~oracle.setOracleLength(value)});
			{knobGUI[5].value_(value)}.defer
		}, midiKnobs[5]).permanent_(true);
		MIDIdef.cc(\FO_continuity, {|v| var value = ControlSpec.specs[\FO_continuity].map(v/127);
			("FO_continuity: "++value).postln;
			~fo_continuity.set(value); ~oracle.setContinuity(value);
			{knobGUI[6].value_(value)}.defer
		}, midiKnobs[6]).permanent_(true);
		MIDIdef.cc(\levelSegments, {|v| var value = ControlSpec.specs[\levelSegments].map(v/127);
			("levelSegments: "++value).postln;
			~levelSegments.set(value);
			{knobGUI[7].value_(value)}.defer
		}, midiKnobs[7]).permanent_(true);
		MIDIdef.cc(\levelRoom, {|v| var value = ControlSpec.specs[\levelRoom].map(v/127);
			("levelRoom: "++value).postln;
			~levelRoom.set(value);
			{knobGUI[8].value_(value)}.defer
		}, midiKnobs[8]).permanent_(true);

		// MIDI BUTTONS
		MIDIdef.noteOn(\button1, {|v| var value = v/127;
			if(activeButton[0] == 0, {~oracle.startListening; "Oracle Start Listening".postln; activeButton[0] = 1}, {~oracle.stopListening; "Oracle Stop Listening".postln; activeButton[0] = 0});
			{buttonGUI[0].value_(activeButton[0])}.defer;
		}, midiButtons[0]).permanent_(true); //oracle listening

		MIDIdef.noteOn(\button2, {|v| var value = v/127;
			if(activeButton[1] == 0, {~oracle.startPlayback; "Oracle Start Generating".postln; activeButton[1] = 1}, {~oracle.stopPlayback; "Oracle Stop Generating".postln; activeButton[1] = 0});
			{buttonGUI[1].value_(activeButton[1])}.defer;
		}, midiButtons[1]).permanent_(true); // oracle generation

		MIDIdef.noteOn(\button3, {|v| var value = v/127;
			if(activeButton[2] == 0, {~oracle.setAutoRebuild(30); "Oracle AutoRebuild ON".postln; activeButton[2] = 1}, {~oracle.setAutoRebuild(0); "Oracle AutoRebuild OFF".postln; activeButton[2] = 0});
			{buttonGUI[2].value_(activeButton[2])}.defer;
		}, midiButtons[2]).permanent_(true); // oracle autorebuild

		MIDIdef.noteOn(\button4, {|v| var value = v/127;
			"Forced Costraint A".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \freqShift, 0, \delay, 0.002, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[1].set(\freq, reson2, \freqShift, 0, \delay, 0.0007, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[2].set(\freq, reson3*3, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(1); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[3]).permanent_(true);

		MIDIdef.noteOn(\button5, {|v| var value = v/127;
			"Forced Costraint B".postlnSpecial;
			~otiacSynths[0].set(\freqShift, 2);
			~otiacSynths[1].set(\freqShift, 2);
			~otiacSynths[2].set(\freqShift, 2);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(1); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[4]).permanent_(true);

		MIDIdef.noteOn(\button6, {|v| var value = v/127;
			"Forced Costraint C".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson1, \impulseFrequency, 300, \durGrain, 0.06, \choose1, 0, \choose2, 1);
			~otiacSynths[1].set(\freq, reson2, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson2, \impulseFrequency, 300, \durGrain, 0.060, \choose1, 0, \choose2, 1);
			~otiacSynths[2].set(\freq, reson3, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson3, \impulseFrequency, 300, \durGrain, 0.06, \choose1, 0, \choose2, 1);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(1); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[5]).permanent_(true);

		MIDIdef.noteOn(\button7, {|v| var value = v/127;
			"Forced Costraint D".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \centreFrequency, reson1, \impulseFrequency, 350, \durGrain, 0.05, \freqShift, 3, \phaseShift, pi/2, \rq, 0.5, \gain, 2, \mix, 0, \choose1, 0.5, \choose2, 0.5, \delay, 0.0013, \ampTresh, 0.5);
			~otiacSynths[1].set(\freq, reson2, \centreFrequency, reson2/2, \impulseFrequency, 300, \durGrain, 0.05, \freqShift, 30, \phaseShift, pi/2, \rq, 1, \gain, 2, \mix, 0, \choose1, 0.5, \choose2, 0.5, \delay, 0.0013, \ampTresh, 0.5);
			~otiacSynths[2].set(\freq, reson3*3, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(1); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[6]).permanent_(true);

		MIDIdef.noteOn(\button8, {|v| var value = v/127;
			"Forced Costraint E".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1*1.2, \freqShift, 0, \delay, 0.002, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[1].set(\freq, reson2*1.2, \freqShift, 0, \delay, 0.0007, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[2].set(\freq, reson3*1.2, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(1); }.defer;
		}, midiButtons[7]).permanent_(true);

		//
		//
		//
		knobGUI[0] = EZKnob(window, 84@100,"Lvl Reson1", \levelReson1).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		knobGUI[1] = EZKnob(window, 84@100,"Lvl Reson2", \levelReson2).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		knobGUI[2] = EZKnob(window, 84@100,"Lvl Reson3", \levelReson3).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		knobGUI[3] = EZKnob(window, 84@100,"Lvl Adaptive", \levelAdaptive).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		knobGUI[4] = EZKnob(window, 84@100,"FO interval", \FO_interval).setColors(Color.red, Color.yellow).font_(Font(awesomeFont, 16));
		knobGUI[5] = EZKnob(window, 84@100,"FO length", \FO_length).setColors(Color.red, Color.yellow).font_(Font(awesomeFont, 16));
		knobGUI[6] = EZKnob(window, 84@100,"FO contin.", \FO_continuity).setColors(Color.red, Color.yellow).font_(Font(awesomeFont, 16));
		knobGUI[7] = EZKnob(window, 84@100,"Lvl FO", \levelSegments).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		knobGUI[8] = EZKnob(window, 84@100,"Lvl Room", \levelRoom).setColors(Color.grey, Color.white).font_(Font(awesomeFont, 16));
		flowWin.nextLine;
		//
		flowWin.shift(analFreqWidth, -75);
		buttonGUI[0] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Start\n Listening", Color.black, Color.new255(0, 200, 0)], ["Stop\n Listening", Color.white, Color.red]]);
		buttonGUI[1] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Start\n Generation", Color.black, Color.new255(0, 200, 0)], ["Stop\n Generation", Color.white, Color.red]]);
		buttonGUI[2] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Start FO\n AutoRebuild", Color.black, Color.new255(0, 200, 0)], ["Stop FO\n AutoRebuild", Color.white, Color.red]]); //.valueAction_(1);
		buttonGUI[3] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Force\n Constraint A", Color.black, Color.new255(0, 200, 0)], ["Forced\n Constraint A", Color.black, Color.yellow]]);
		buttonGUI[4] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Force\n Constraint B", Color.black, Color.new255(0, 200, 0)], ["Forced\n Constraint B", Color.black, Color.yellow]]);
		buttonGUI[5] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Force\n Constraint C", Color.black, Color.new255(0, 200, 0)], ["Forced\n Constraint C", Color.black, Color.yellow]]);
		buttonGUI[6] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Force\n Constraint D", Color.black, Color.new255(0, 200, 0)], ["Forced\n Constraint D", Color.black, Color.yellow]]);
		buttonGUI[7] = Button(window, 95@50).font_(Font(awesomeFont, 16)).states_([["Force\n Constraint E", Color.black, Color.new255(0, 200, 0)], ["Forced\n Constraint E", Color.black, Color.yellow]]);
		//
		if(setupFile.notNil, {this.loadJSONonlyMIDI(setupFile)});
		//
		//
		//
		//
		//
		window.onClose_({this.free});
	}

	updateMIDIdefs {|updatedKnobs = nil, updatedButtons = nil|
		// Free existing MIDIdefs to avoid conflicts
		[ \levelReson1, \levelReson2, \levelReson3, \levelAdaptive, \FO_interval, \FO_length, \FO_continuity, \levelSegments, \levelRoom,\button1, \button2, \button3, \button4, \button5, \button6, \button7, \button8 ].do({ |key| MIDIdef(key).free });

		// Parse current values from TextFields to ensure midiKnobs and midiButtons are updated
		midiKnobs = updatedKnobs ?? {knobsField.value.stringToArray};
		midiButtons = updatedButtons ?? {toggleField.value.stringToArray ++ holdField.value.stringToArray};

		// Recreate MIDI knobs with current values
		MIDIdef.cc(\levelReson1, {|v| var value = ControlSpec.specs[\levelReson1].map(v/127);
			("levelReson1: "++value).postln;
			~levelReson1.set(value);
			{knobGUI[0].value_(value)}.defer
		}, midiKnobs[0]).permanent_(true);

		MIDIdef.cc(\levelReson2, {|v| var value = ControlSpec.specs[\levelReson2].map(v/127);
			("levelReson2: "++value).postln;
			~levelReson2.set(value);
			{knobGUI[1].value_(value)}.defer
		}, midiKnobs[1]).permanent_(true);

		MIDIdef.cc(\levelReson3, {|v| var value = ControlSpec.specs[\levelReson3].map(v/127);
			("levelReson3: "++value).postln;
			~levelReson3.set(value);
			{knobGUI[2].value_(value)}.defer
		}, midiKnobs[2]).permanent_(true);

		MIDIdef.cc(\levelAdaptive, {|v| var value = ControlSpec.specs[\levelAdaptive].map(v/127);
			("levelAdaptive: "++value).postln;
			~levelAdaptive.set(value);
			{knobGUI[3].value_(value)}.defer
		}, midiKnobs[3]).permanent_(true);

		MIDIdef.cc(\FO_interval, {|v| var value = ControlSpec.specs[\FO_interval].map(v/127);
			("FO_interval: "++value).postln;
			~fo_interval.set(value); ~oracle.setGenerationInterval(value);
			{knobGUI[4].value_(value)}.defer
		}, midiKnobs[4]).permanent_(true);

		MIDIdef.cc(\FO_length, {|v| var value = ControlSpec.specs[\FO_length].map(v/127);
			("FO_length: "++value).postln;
			~fo_length.set(value);
			if(value == 1.neg, {~oracle.setOracleLength(nil)}, {~oracle.setOracleLength(value)});
			{knobGUI[5].value_(value)}.defer
		}, midiKnobs[5]).permanent_(true);

		MIDIdef.cc(\FO_continuity, {|v| var value = ControlSpec.specs[\FO_continuity].map(v/127);
			("FO_continuity: "++value).postln;
			~fo_continuity.set(value); ~oracle.setContinuity(value);
			{knobGUI[6].value_(value)}.defer
		}, midiKnobs[6]).permanent_(true);

		MIDIdef.cc(\levelSegments, {|v| var value = ControlSpec.specs[\levelSegments].map(v/127);
			("levelSegments: "++value).postln;
			~levelSegments.set(value);
			{knobGUI[7].value_(value)}.defer
		}, midiKnobs[7]).permanent_(true);

		MIDIdef.cc(\levelRoom, {|v| var value = ControlSpec.specs[\levelRoom].map(v/127);
			("levelRoom: "++value).postln;
			~levelRoom.set(value);
			{knobGUI[8].value_(value)}.defer
		}, midiKnobs[8]).permanent_(true);

		// Recreate MIDI buttons with current values
		MIDIdef.noteOn(\button1, {|v| var value = v/127;
			if(activeButton[0] == 0, {~oracle.startListening; "Oracle Start Listening".postln; activeButton[0] = 1}, {~oracle.stopListening; "Oracle Stop Listening".postln; activeButton[0] = 0});
			{buttonGUI[0].value_(activeButton[0])}.defer;
		}, midiButtons[0]).permanent_(true);

		MIDIdef.noteOn(\button2, {|v| var value = v/127;
			if(activeButton[1] == 0, {~oracle.startPlayback; "Oracle Start Generating".postln; activeButton[1] = 1}, {~oracle.stopPlayback; "Oracle Stop Generating".postln; activeButton[1] = 0});
			{buttonGUI[1].value_(activeButton[1])}.defer;
		}, midiButtons[1]).permanent_(true);

		MIDIdef.noteOn(\button3, {|v| var value = v/127;
			if(activeButton[2] == 0, {~oracle.setAutoRebuild(30); "Oracle AutoRebuild ON".postln; activeButton[2] = 1}, {~oracle.setAutoRebuild(0); "Oracle AutoRebuild OFF".postln; activeButton[2] = 0});
			{buttonGUI[2].value_(activeButton[2])}.defer;
		}, midiButtons[2]).permanent_(true);

		MIDIdef.noteOn(\button4, {|v| var value = v/127;
			"Forced Costraint A".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \freqShift, 0, \delay, 0.002, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[1].set(\freq, reson2, \freqShift, 0, \delay, 0.0007, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[2].set(\freq, reson3*3, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(1); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[3]).permanent_(true);

		MIDIdef.noteOn(\button5, {|v| var value = v/127;
			"Forced Costraint B".postlnSpecial;
			~otiacSynths[0].set(\freqShift, 2);
			~otiacSynths[1].set(\freqShift, 2);
			~otiacSynths[2].set(\freqShift, 2);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(1); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[4]).permanent_(true);

		MIDIdef.noteOn(\button6, {|v| var value = v/127;
			"Forced Costraint C".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson1, \impulseFrequency, 300, \durGrain, 0.06, \choose1, 0, \choose2, 1);
			~otiacSynths[1].set(\freq, reson2, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson2, \impulseFrequency, 300, \durGrain, 0.060, \choose1, 0, \choose2, 1);
			~otiacSynths[2].set(\freq, reson3, \freqShift, 1, \windowsize, 512, \rq, 0.5, \gain, 1, \centreFrequency, reson3, \impulseFrequency, 300, \durGrain, 0.06, \choose1, 0, \choose2, 1);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(1); buttonGUI[6].value_(0); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[5]).permanent_(true);

		MIDIdef.noteOn(\button7, {|v| var value = v/127;
			"Forced Costraint D".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1, \centreFrequency, reson1, \impulseFrequency, 350, \durGrain, 0.05, \freqShift, 3, \phaseShift, pi/2, \rq, 0.5, \gain, 2, \mix, 0, \choose1, 0.5, \choose2, 0.5, \delay, 0.0013, \ampTresh, 0.5);
			~otiacSynths[1].set(\freq, reson2, \centreFrequency, reson2/2, \impulseFrequency, 300, \durGrain, 0.05, \freqShift, 30, \phaseShift, pi/2, \rq, 1, \gain, 2, \mix, 0, \choose1, 0.5, \choose2, 0.5, \delay, 0.0013, \ampTresh, 0.5);
			~otiacSynths[2].set(\freq, reson3*3, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(1); buttonGUI[7].value_(0);}.defer;
		}, midiButtons[6]).permanent_(true);

		MIDIdef.noteOn(\button8, {|v| var value = v/127;
			"Forced Costraint E".postlnSpecial;
			~otiacSynths[0].set(\freq, reson1*1.2, \freqShift, 0, \delay, 0.002, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[1].set(\freq, reson2*1.2, \freqShift, 0, \delay, 0.0007, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			~otiacSynths[2].set(\freq, reson3*1.2, \freqShift, 0, \delay, 0.0004, \rq, 0.5, \gain, 1, \choose1, 1, \choose2, 0);
			{buttonGUI[3].value_(0); buttonGUI[4].value_(0); buttonGUI[5].value_(0); buttonGUI[6].value_(0); buttonGUI[7].value_(1); }.defer;
		}, midiButtons[7]).permanent_(true);

		"MIDI definitions updated.".postlnSpecial(~otiac_logWindow);
	}

	onClose {
		this.free;
	}

	free {
		server.freeAll;
		[analIn, analTrans].do(_.free);
		[analOutLeft, analOutRight, analOtiac].do(_.kill);
	}
}


// a very simple method to print in the post window and into a TextView used in OTIAC_GUI
+ String{
	postlnSpecial { arg logWindow = ~otiac_logWindowTwo;
		this.postln;
		{ logWindow.setString(this++"\n")}.defer;
	}
}

// a method to convert a string of integers to array: "45, 46, 78" ---> [45, 46, 78]
+ String{
	stringToArray {
		var array, result;
		array = this.split($,);
		result = array.collect({|item| item.asInteger});
		^result;
	}
}

// EOF