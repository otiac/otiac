FactorOracle {
	var <states;           // Array of states, each containing transitions
	var <suffixLinks;      // Suffix link array for the oracle
	var <lrs;              // Length of repeated suffix array
	var <eventSequence;    // Internal sequence of [duration, pitch] events
	var <alphabetMap;      // Map from symbols to indices
	var <reverseMap;       // Map from indices back to symbols
	var <inputPitches;     // Original pitch array
	var <inputRhythm;      // Original rhythm array

	// weighted Factor Oracle (to be developed for the future version with RL)
	var <forwardWeights;    // Dictionary: state -> symbol -> weight
	var <suffixWeights;     // Dictionary: state -> weight

	*new { |pitches, rhythm|
		^super.new.init(pitches, rhythm);
	}

	*newFromEvents { |events|
		^super.new.initFromEvents(events);
	}

	initFromEvents { |events|
		// Store original data
		inputPitches = events.collect { |e| e[1] };
		inputRhythm = events.collect { |e| e[0] };

		// Create internal event sequence directly
		// Each event is [duration, pitch] where duration can be symbol or number
		eventSequence = events.collect { |event|
			var duration = event[0];
			var pitch = this.normalizePitch(event[1]);
			[duration, pitch]
		};

		// Build the oracle
		this.buildOracle;
	}

	init { |pitches, rhythm|
		inputPitches = pitches;
		inputRhythm = rhythm;

		// Initialize weights (all 0.0)
		forwardWeights = Dictionary.new;
		suffixWeights = Dictionary.new;

		states.size.do { |i|
			suffixWeights[i] = 0.0;
			forwardWeights[i] = Dictionary.new;
		};

		// Validate input compatibility
		this.validateInput(pitches, rhythm);

		// Create internal event sequence
		eventSequence = this.createEventSequence(pitches, rhythm);

		// Build the oracle
		this.buildOracle;
	}

	// Validate that pitches and rhythm arrays are compatible
	validateInput { |pitches, rhythm|
		var positiveRhythmCount = rhythm.count { |dur| dur > 0 };

		if (pitches.size != positiveRhythmCount, {
			Error("Input validation failed: pitches array size (%) does not match positive rhythm count (%)".format(
				pitches.size, positiveRhythmCount
			)).throw;
		});
	}

	// Create internal event sequence from pitch and rhythm arrays
	createEventSequence { |pitches, rhythm|
		var events = List.new;
		var pitchIndex = 0;

		rhythm.do({ |duration|
			if (duration > 0, {
				// Note event: [duration, pitch]
				var pitch = this.normalizePitch(pitches[pitchIndex]);
				events.add([duration, pitch]);
				pitchIndex = pitchIndex + 1;
			}, {
				// Rest event: [duration] (negative duration)
				events.add([duration]);
			});
		});

		^events.asArray;
	}

	// Normalize pitch to handle both single values and chords
	normalizePitch { |pitch|
		if(pitch.isArray, {
			// Sort chords for consistent comparison
			^pitch.asArray.sort;
		}, {
			// Convert single values to arrays for uniform handling
			^[pitch];
		});
	}

	// Create a unique key for each event (duration + pitch combination)
	makeEventKey { |event|
		var duration = event[0];
		if(event.size == 1, {
			// Rest event
			^("rest_" ++ duration.abs.asString).asSymbol;
		}, {
			// Note event
			var pitchStr = event[1].collect(_.asString).join("_");
			^(duration.asString ++ "_" ++ pitchStr).asSymbol;
		});
	}

	buildOracle {
		var n = eventSequence.size;
		var alphabet;

		// Build alphabet mapping using event keys
		alphabet = eventSequence.collect { |event| this.makeEventKey(event) }.asSet.asArray.sort;
		alphabetMap = Dictionary.new;
		reverseMap = Dictionary.new;

		alphabet.do({ |symbol, i|
			alphabetMap[symbol] = i;
			// Store the original event in reverseMap
			reverseMap[i] = eventSequence.detect { |event|
				this.makeEventKey(event) == symbol
			};
		});

		// Initialize oracle structures
		states = Array.fill(n + 1, { Dictionary.new });
		suffixLinks = Array.fill(n + 1, { -1 });
		lrs = Array.fill(n + 1, { 0 });

		// Build the oracle
		suffixLinks[0] = -1;
		lrs[0] = 0;

		n.do({ |i|
			var symbol = this.makeEventKey(eventSequence[i]);
			var symbolIndex = alphabetMap[symbol];
			var k, p;

			// Add forward transition
			states[i][symbolIndex] = i + 1;

			// Find suffix link
			k = suffixLinks[i];
			while {(k > -1) and: { states[k][symbolIndex].isNil }} {
				states[k][symbolIndex] = i + 1; // Add backward transition
				k = suffixLinks[k];  // Follow suffix chain
			};

			if (k == -1) { // Reached root
				suffixLinks[i + 1] = 0;
				lrs[i + 1] = 0;
			} {
				p = states[k][symbolIndex]; // Found matching state
				if (p.notNil and: { p <= i }) {
					suffixLinks[i + 1] = p;
					lrs[i + 1] = min(lrs[p] + 1, lrs[i] + 1);
				} {
					suffixLinks[i + 1] = 0;
					lrs[i + 1] = 0;
				};
			};
		});

		^this;
	}

	// Format output event back to desired format
	formatOutputEvent { |event|
		if (event.size == 1) {
			// Rest: [duration] (negative)
			^[event[0]];
		} {
			// Note: [duration, pitch]
			var duration = event[0];
			var pitch = event[1];
			var formattedPitch = if (pitch.size == 1) { pitch[0] } { pitch };
			^[duration, formattedPitch];
		};
	}

	// Generate a new sequence using the Factor Oracle
	// Because of length and continuity paramenters, it can happen that the FO reaches the end too quickly:
	// If this happens, the oracle jumps to a random state
	// Otherwise, lenght can be set as NIL and the sequence will keep generating until reaches the end (or a max of 101 events, to prevent looping forever)
	generate { |length = nil, continuity = 0.5, startState = 0|
		var output = List.new;
		var statePath = List.new;
		var currentState = startState;
		var maxLength = length ?? 101; //(eventSequence.size * 2);
		var count = 0;
		var break = false;

		while { (count < maxLength) and: break.not and: { currentState.notNil } } {
			var transitions = states[currentState];
			var nextState = nil;
			var outputEvent = nil;

			if (transitions.size > 0, {
				// Choose between forward transitions and suffix link
				if ((suffixLinks[currentState] > 0) and: { continuity.coin.not }) {
					// Jump via suffix link for variation
					nextState = suffixLinks[currentState];
					if (currentState < eventSequence.size) {
						outputEvent = eventSequence[currentState];
					};
				} {
					// Follow a forward transition
					var keys = transitions.keys.asArray;
					var chosenKey = keys.choose;
					nextState = transitions[chosenKey];
					outputEvent = reverseMap[chosenKey];
				};

				if (outputEvent.notNil, {
					output.add(this.formatOutputEvent(outputEvent));
					statePath.add(currentState);
					count = count + 1;
				});
				currentState = nextState;

				// Reset if we reach the end
				if (currentState >= states.size, {
					if (length.notNil, {
						currentState = (states.size - 1).rand;
					}, {
						break = true;
					});
				});
			}, {
				// No transitions available (FO reached the end or because of continuity value), then jump to random state
				if (length.notNil, {
					currentState = states.size.rand;
				}, {
					break = true;
				});
			});
		};

		^FactorOracleResult(output.asArray, statePath.asArray);
	}
	// Generate with specific starting context
	generateFromContext { |contextPitches, contextRhythm, length = nil, continuity = 0.5|
		var contextEvents = this.createEventSequence(contextPitches, contextRhythm);
		var startState = this.findBestMatch(contextEvents);
		^this.generate(length, continuity, startState);
	}

	// Find the best matching state for a given context
	findBestMatch { |contextEvents|
		var bestState = 0;
		var bestLength = 0;

		states.size.do { |state|
			var matchLength = 0;
			var tempState = state;

			contextEvents.reverseDo { |event|
				var symbol = this.makeEventKey(event);
				var symbolIndex = alphabetMap[symbol];
				var found = false;

				// Check if we can reach this symbol from current state
				while { (tempState > 0) and: { found.not } } {
					if (tempState > 0 and: { tempState <= eventSequence.size }) {
						if (this.makeEventKey(eventSequence[tempState - 1]) == symbol) {
							found = true;
							matchLength = matchLength + 1;
							tempState = tempState - 1;
						} {
							tempState = suffixLinks[tempState];
						};
					} {
						tempState = 0;
					};
				};

				if (found.not) {
					tempState = 0;
				};
			};

			if (matchLength > bestLength) {
				bestLength = matchLength;
				bestState = state;
			};
		};

		^bestState;
	}

	// Analyze the oracle structure
	analyze {
		var info = Dictionary.new;
		var totalTransitions = 0;
		var maxSuffixLink = 0;
		var avgLRS = 0;
		var chordSizes = List.new;
		var noteDurations = List.new;
		var restDurations = List.new;

		states.do({ |state|
			totalTransitions = totalTransitions + state.size;
		});

		suffixLinks.do({ |link|
			if (link > maxSuffixLink, {
				maxSuffixLink = link;
			});
		});

		avgLRS = lrs.sum / lrs.size;

		eventSequence.do { |event|
			if(event.size == 1, {
				// Rest
				restDurations.add(event[0].abs);
			},{
				// Note
				noteDurations.add(event[0]);
				chordSizes.add(event[1].size);
			});
		};

		info[\numStates] = states.size;
		info[\numTransitions] = totalTransitions;
		info[\maxSuffixLink] = maxSuffixLink;
		info[\averageLRS] = avgLRS;
		info[\alphabetSize] = alphabetMap.size;
		info[\numEvents] = eventSequence.size;
		info[\numNotes] = eventSequence.count { |e| e.size > 1 };
		info[\numRests] = eventSequence.count { |e| e.size == 1 };

		if(chordSizes.size > 0, {
			info[\avgChordSize] = chordSizes.mean;
			info[\maxChordSize] = chordSizes.maxItem;
			info[\numChords] = chordSizes.count { |size| size > 1 };
			info[\numSinglePitches] = chordSizes.count { |size| size == 1 };
		});

		if(noteDurations.size > 0, {
			info[\avgNoteDuration] = noteDurations.mean;
			info[\totalNoteDuration] = noteDurations.sum;
		});

		if(restDurations.size > 0, {
			info[\avgRestDuration] = restDurations.mean;
			info[\totalRestDuration] = restDurations.sum;
		});

		^info;
	}

	// Print structure for debugging
	printStructure {
		"=== Factor Oracle Structure ===".postln;
		"Events structure : [rhythm, pitch/chord]\n".postln;
		"".postln;
		("Number of states: " ++ states.size).postln;
		("Alphabet size: " ++ alphabetMap.size).postln;
		("Total events: " ++ eventSequence.size).postln;
		("Notes: " ++ eventSequence.count { |e| e.size > 1 }).postln;
		("Rests: " ++ eventSequence.count { |e| e.size == 1 }).postln;
		"".postln;

		states.size.do({ |i|
			("State " ++ i ++ ":").postln;
			if(i < eventSequence.size, {
				("  Event: " ++ this.formatOutputEvent(eventSequence[i])).postln;
			});
			("  Transitions: " ++ states[i].size).postln;
			("  Suffix link: " ++ suffixLinks[i]).postln;
			("  LRS: " ++ lrs[i]).postln;
		});
	}

	printStructureString { var result = "";
		result = result++"=== Factor Oracle Structure ===\n";
		result = result++"Events structure : [rhythm, pitch/chord]\n";
		result = result++"\n";
		result = result++format("Number of states: %\n", states.size);
		result = result++format("Alphabet size: %\n", alphabetMap.size);
		result = result++format("Total events: %\n", eventSequence.size);
		result = result++format("Notes: %\n", eventSequence.count { |e| e.size > 1 });
		result = result++format("Rests: %\n", eventSequence.count { |e| e.size == 1 });
		result = result++"\n";

		states.size.do({ |i|
			result = result++format("State %:\n", i);
			if(i < eventSequence.size, {
				result = result++format("  Event: %\n", this.formatOutputEvent(eventSequence[i]));
			});
			result = result++format("  Transitions: %\n", states[i].size);
			result = result++format("  Suffix link: %\n", suffixLinks[i]);
			result = result++format("  LRS: %\n", lrs[i]);
		});
		^result;
	}

	// Factor Oracle visualization
	drawFactorOracle {
		arg width = 1500, height = 500, stateLabels = false, windowName = "FactorOracle visualization";
		var nStates = this.suffixLinks.size; //Length of the oracle
		var winFO, transitions, currentState = 0, posX, nextX, currentX, arcHeight, otherStates;
		var lrsThreshold = 0;
		var circleWidth = (0.5/nStates*width)/1.5;
		Window.closeAll;
		winFO = Window.new(
			windowName,
			Rect(1400, 500, width, height),
			resizable: true,
			scroll: true,
		).alwaysOnTop_(true).front;

		winFO.drawFunc = {
			Pen.strokeColor_(Color.black);
			nStates.do({|i|
				// Position for each state's circle
				posX = (i/nStates * width) + (0.5*1.0/nStates*width);
				// Iterate over forward transitions
				transitions = this.states.size;
				transitions.do({|tran|
					// If forward transition to next state
					if(tran == (i+1), {
						// Draw forward transitions
						nextX = ((i + 1)/nStates*width) + (0.5/nStates*width);
						currentX = posX + (0.25 / nStates * width);
						Pen.strokeColor = Color.black;
						Pen.width = 0.5;
						Pen.line(Point(currentX, height/2), Point(nextX, height/2));
						Pen.stroke;
						// Add labels with the state
						if(stateLabels, {
							StaticText(winFO, Rect(currentX, height/5, circleWidth*2, height/2)) // <<<--- TO BE IMPROVED
							.string_(this.eventSequence[i][1].asString)
							.align_(\center)
							.font_(Font("Baskerville", circleWidth/1.5))
							.stringColor_(Color.black);
						});
					},
					{
						if(this.lrs[tran] >= lrsThreshold, {
							// Forward transition to another state
							otherStates = this.states[i].values.asArray;
							otherStates.remove(i+1); // Remove the forward transition to next state because already drawn
							otherStates.do({|state, idx|
								currentX = posX;
								nextX = (state/ nStates * width) + (0.5 / nStates * width);
								arcHeight = (state-i).linlin(0, nStates, height/4, 10-height/2); // Alternative fixed arc: 10-height/2
								Pen.strokeColor = Color.black;
								Pen.width = 0.5;
								Pen.moveTo(Point(currentX+(circleWidth/2), height/2));
								Pen.quadCurveTo(Point(nextX+(circleWidth/2), height/2), Point((currentX+nextX)/2, arcHeight));
								Pen.stroke;
							});

						});
					});
				});

				if((this.suffixLinks[i].notNil && this.suffixLinks[i]!=1.neg) && (this.lrs[i] >= lrsThreshold), {
					currentX = posX;
					nextX = (this.suffixLinks[i] / nStates * width) + (0.5 / nStates * width);
					// Draw arc
					arcHeight = (i-this.suffixLinks[i]).linlin(0, nStates, height/1.5, height*1.5); // Alternative fixed arc: height
					Pen.strokeColor = Color.red;
					Pen.width = 0.5;
					Pen.moveTo(Point(nextX+(circleWidth/2), height/2));
					Pen.quadCurveTo(Point(currentX+(circleWidth/2), height/2), Point((currentX+nextX)/2, arcHeight));
					Pen.stroke;
				});
			});
			//Draw circles on top
			nStates.do({|i|
				posX = (i/nStates * width) + (0.5 * 1.0 / nStates * width);
				Pen.fillColor = Color.black;
				Pen.fillOval(Rect(posX, height/2 - (circleWidth/2), circleWidth, circleWidth));
				// Add state number on the oval
				StaticText(winFO, Rect(posX+(circleWidth/30), height/2 - (circleWidth/2), circleWidth, circleWidth))
				.string_(i)
				.align_(\center)
				.font_(Font("Baskerville", circleWidth/1.5))
				.stringColor_(Color.white);
			});
		}

	}
}

// Result class to hold generated sequences with accessor methods
FactorOracleResult {
	var <eventArray;
	var <statePath;

	*new { |eventArray, statePath|
		^super.newCopyArgs(eventArray, statePath);
	}

	// Get just the pitch content (excluding rests)
	pitchContent {
		^eventArray.select { |event| event.size > 1 }.collect { |event| event[1] };
	}

	// Get just the rhythm content (all durations)
	rhythmContent {
		^eventArray.collect { |event| event[0] };
	}

	// Get the full event array
	asArray {
		^eventArray;
	}

	// Print formatted output
	postln {
		"Generated sequence:".postln;
		eventArray.do { |event, i|
			var stateInfo = if(statePath.notNil, { " (state: " ++ statePath[i] ++ ")" }, { "" });
			if(event.size == 1, {
				("  " ++ i ++ ": REST " ++ event[0] ++ stateInfo).postln;
			}, {
				("  " ++ i ++ ": " ++ event[0] ++ " -> " ++ event[1] ++ stateInfo).postln;
			});
		};
	}
}