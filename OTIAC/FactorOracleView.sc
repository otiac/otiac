FactorOracleView {
	var <window, <userView;            // Window and userView for embedding in other Windows
	var <width = 1400, <height = 500;  // Dimensions
	var <aFactorOracle;                // The Factor Oracle
	var <windowName;                   // Window name to be displayed
	var <statesSequence;               // Sequence of states
	var <highlightStep = 0;            // The current state to highlight
	var <>stateLabels = false;         // Add/remove state labels

	*new { |anFO, generatedSequence = nil, step = nil, name = "Factor Oracle Visualization", windowWidth = 1400, windowHeight = 500, x = 100, y = 100, parent = nil|
		^super.new.init(anFO, generatedSequence, step, name, windowWidth, windowHeight, x, y, parent);
	}

	init { |anFO,  generatedSequence, step, name, windowWidth, windowHeight, x, y, parent|
		// Create window
		windowName = name;
		if(parent.isKindOf(Window),
			{window = parent},
			{window = Window(name, Rect(x, y, windowWidth, windowHeight))
				.background_(Color.new255(230, 230, 230))
				.alwaysOnTop_(true)
				.front;});
		aFactorOracle = anFO;
		width = windowWidth;
		height = windowHeight;
		statesSequence = generatedSequence ?? (-1..aFactorOracle.eventSequence.size);
		highlightStep = step ?? 0;
		userView = UserView(window, width@height);
		userView.drawFunc = { this.draw };
	}

	draw {
		var highlightStates = [statesSequence[highlightStep], statesSequence[highlightStep+1] ?? statesSequence[highlightStep]];
		var nStates = aFactorOracle.suffixLinks.size; //length of the oracle
		var transitions, currentState = 0, posX, nextX, currentX, arcHeight, otherStates;
		var lrsThreshold = 0;
		var circleWidth = (0.5/nStates*width)/1.5;
		var strokeWidthVal = circleWidth.linlin(4, 42, 1, 7, clip: \nil);
		//format("Transition states: % -> %", highlightStates[0], highlightStates[1]).postln; //post the transition

		//userView title
		Pen.stringAtPoint(this.windowName, 5@5, Font("Baskerville", width/(windowName.size.explin(1, 30, 22, 30, clip: \nil))), Color.black;);
		//number of states
		Pen.stringAtPoint("Total states: "++nStates, 5@60, Font("Baskerville", 18), Color.black;);

		Pen.strokeColor_(Color.black);
		nStates.do({|i|
			//position for each state's circle
			posX = (i/nStates * width) + (0.5*1.0/nStates*width);
			//iterate over forward transitions
			transitions = aFactorOracle.states.size;
			transitions.do({|tran|
				// if forward transition to next state
				if(tran == (i+1), {
					// draw forward transitions
					nextX = ((i + 1)/nStates*width) + (0.5/nStates*width);
					currentX = posX + (0.25 / nStates * width);
					Pen.strokeColor = Color.black;
					if((i == highlightStates[0]) && (tran == highlightStates[1]), {Pen.width = strokeWidthVal}, {Pen.width = 0.5});
					//Pen.width = 0.5;
					Pen.line(Point(currentX, height/2), Point(nextX, height/2));
					Pen.stroke;
					// add labels with the state
					if(stateLabels, {
						StaticText(userView, Rect(currentX, height/5, circleWidth*2, height/2)) // <<<--- TO BE IMPROVED
						.string_(aFactorOracle.eventSequence[i][1].asString)
						.align_(\center)
						.font_(Font("Baskerville", circleWidth/1.5))
						.stringColor_(Color.black);
					});
				},
				{
					if(aFactorOracle.lrs[tran] >= lrsThreshold, {
						// forward transition to another state
						otherStates = aFactorOracle.states[i].values.asArray;
						otherStates.remove(i+1); // remove the forward transition to next state because already drawn
						otherStates.do({|state, idx|
							currentX = posX;
							nextX = (state/ nStates * width) + (0.5 / nStates * width);
							arcHeight = (state-i).linlin(0, nStates, height/4, 10-height/2); // Alternative fixed arc: 10-height/2
							Pen.strokeColor = Color.black;
							if((i==highlightStates[0]) && (state == highlightStates[1]), {Pen.width = strokeWidthVal}, {Pen.width = 0.5});
							//Pen.width = 0.5;
							Pen.moveTo(Point(currentX+(circleWidth/2), height/2));
							Pen.quadCurveTo(Point(nextX+(circleWidth/2), height/2), Point((currentX+nextX)/2, arcHeight));
							Pen.stroke;
						});

					});
				});
			});

			if((aFactorOracle.suffixLinks[i].notNil && aFactorOracle.suffixLinks[i]!=1.neg) && (aFactorOracle.lrs[i] >= lrsThreshold), {
				currentX = posX;
				nextX = (aFactorOracle.suffixLinks[i] / nStates * width) + (0.5 / nStates * width);
				// draw arc
				arcHeight = (i-aFactorOracle.suffixLinks[i]).linlin(0, nStates, height/1.5, height*1.5); // Alternative fixed arc: height
				Pen.strokeColor = Color.red;
				if((i == highlightStates[0]) && (aFactorOracle.suffixLinks[i] == highlightStates[1]), {Pen.width = strokeWidthVal}, {Pen.width = 0.5});
				Pen.moveTo(Point(nextX+(circleWidth/2), height/2));
				Pen.quadCurveTo(Point(currentX+(circleWidth/2), height/2), Point((currentX+nextX)/2, arcHeight));
				Pen.stroke;
			});
		});
		//draw circles on top
		nStates.do({|i|
			posX = (i/nStates * width) + (0.5 * 1.0 / nStates * width);
			if(i == highlightStates[0], {Pen.fillColor = Color.red;}, {Pen.fillColor = Color.black;});
			Pen.fillOval(Rect(posX, height/2 - (circleWidth/2), circleWidth, circleWidth));
			// add state number on the oval
			StaticText(userView, Rect(posX+(circleWidth/30), height/2 - (circleWidth/2), circleWidth, circleWidth))
			.string_(i)
			.align_(\center)
			.font_(Font("Baskerville", circleWidth/1.5))
			.stringColor_(Color.white);
		});
	}

	// update with a new sequence and state
	update { |newSequence, newValue|
		statesSequence = newSequence;
		highlightStep = newValue;
		userView.refresh;
	}

	// Give an abritrary step to draw
	gotoStep { |newValue|
		newValue = newValue+1;
		this.stateWarning(newValue);
		highlightStep = newValue;
		userView.refresh;
	}

	// Draw the connection to the next step in the already given sequence
	nextStep {
		highlightStep = highlightStep + 1;
		this.stateWarning(highlightStep);
		userView.refresh;
	}

	// Draw the connection to the previous step in the already given sequence
	previousStep {
		highlightStep = highlightStep - 1;
		this.stateWarning(highlightStep);
		userView.refresh;
	}

	stateWarning {|step|
		if(step < 0, {"No state.".warn;});
		if(step == (this.statesSequence.size-1), {"Careful, reached the end of given sequence length.".warn;});
		if(step > (this.statesSequence.size-1), {"State out of given sequence length.".warn;});
	}

	addLabels {
		stateLabels = true;
		userView.refresh;
	}

	removeLabels {
		stateLabels = false;
		userView.refresh;
	}

	refresh {
		userView.refresh; // Redraw
	}

	close {
		window.close;
	}
}