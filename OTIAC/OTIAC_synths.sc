OTIAC_synths {
	*load { |server|
		// the needed buses
		~outGuitar = Bus.audio(server, 1); // Don't touch!
		~outSegments = Bus.audio(server, 1); // Don't touch!
		~outRoom = Bus.audio(server, 1); // don't touch
		~monitorBus = Bus.audio(server, 1); // Don't touch!
		~busOTIAC = Bus.audio(server, 1); // Don't touch!
		~otiacSynths = Array.fill(4, {0}); // Don't touch!


		// synth used for the calibration
		SynthDef(\checkariell, { arg min = 0.006, max = 0.0075, inBus, outBus;
			var input, inRange, tooLow, tooHigh, amp, rms;
			input = SoundIn.ar(inBus);
			//amp = Amplitude.ar(input).poll(label: \amp);
			rms = RunningSum.rms(Amplitude.ar(input), 4800); //.poll(label: \rms);
			inRange = InRange.kr(rms, min, max);
			tooLow = InRange.kr(rms, 0, min);
			tooHigh = InRange.kr(rms, max, 1);
			SendReply.kr(Impulse.kr(15), '/ampCheck', [inRange, tooLow, tooHigh]);
			Out.ar(outBus, WhiteNoise.ar(0.1));
		}).add;

		// synth for testing the most basic feedback
		SynthDef(\theMostBasicFeedback, {
			arg busIn = 0, busOut = 0, gate = 1;
			var inMic, input, envfolIn, control, cutoffEnv;

			inMic = LeakDC.ar(SoundIn.ar(busIn));
			input = Compander.ar(inMic, inMic);
			envfolIn = EnvFollow.ar(input);
			control = (1-envfolIn).abs;
			cutoffEnv = EnvGen.kr(Env.cutoff(1), gate, doneAction: Done.freeSelf);

			Out.ar(busOut, input*control*cutoffEnv);
		}).add;

		// the actual OTIAC synth
		SynthDef(\otiac, {
			arg gain = 0, busIn = 0, busOut = 0, busOTIAC = 1, lag = 0.5, delay = 0.0107, ampTresh = 0.2, freq = 200, centreFrequency = 100, rq = 0.5, impulseFrequency = 1, durGrain = 0.1, relaxTime = 0.5, choose1 = 0, choose2 = 1, windowsize = 4096, wipe = 0, lfFreq = 1, urLag = 3, freqShift = 1, phaseShift = 0, levelOut = 0, outMic;
			var inMic, input, envfolIn, control, bandpass, ctrlBandpass, ctrlGrain, chain, trig1, realTrig1, grain, env, output;

			inMic = LeakDC.ar(SoundIn.ar(busIn))*gain.lag3(0.5*urLag);
			input = Compander.ar(inMic, inMic); //input mic
			envfolIn = EnvFollow.ar(input);
			control = (1-envfolIn).abs;

			bandpass = BPF.ar(input, freq, 0.75);
			bandpass = DelayC.ar(FreqShift.ar(bandpass, freqShift, phaseShift), 2, delay);
			ctrlBandpass = (1-EnvFollow.ar(bandpass)).abs;
			bandpass = bandpass*ctrlBandpass;

			trig1 = envfolIn > ampTresh;
			// to exclude potential new triggers occurring in the next relaxTime seconds
			realTrig1 = Trig1.ar((Timer.ar(trig1) > relaxTime.lag3(0.1))*trig1, relaxTime.lag3(0.1));

			grain = GrainIn.ar(
				numChannels: 1,
				trigger: Impulse.ar(impulseFrequency.lag3(1*urLag)), //Impulse.ar(envfolIn.expexp(0.0001, 1.0, freq, 11).lag3(urLag)), //Impulse.ar(impulseFrequency.lag3(1*urLag)),
				dur: durGrain.lag3(1*urLag), //envfolIn.expexp(0.0001, 1.0, 0.1, 0.001).lag3(urLag), //durGrain.lag3(1*urLag),
				in: Mix.new([input*(1-choose1.lag3(0.1*urLag)), bandpass*choose1.lag3(0.1*urLag)])
			);
			ctrlGrain = (1-EnvFollow.ar(grain)).abs;

			env = EnvGen.ar(Env.asr(0.01, 0.5, 1), 1-realTrig1);
			output = BPF.ar(grain, centreFrequency.lag3(1*urLag), rq.lag3(1*urLag))*ctrlGrain*env;

			// Parameter choose1 picks only the bandpass
			// Parameter choose2 picks the granulation
			Out.ar([busOut, busOTIAC], levelOut.lag3(0.1)*Limiter.ar(bandpass*control, 0.7)*(1-choose2.lag3(0.1*urLag)) );
			Out.ar([busOut, busOTIAC], levelOut.lag3(0.1)*Limiter.ar(output)*choose2.lag3(0.1*urLag) );
			Out.ar(outMic, inMic); //only for inMic monitoring
		}).add;

		// synthe with the adaptive centroid
		SynthDef(\otiacAdapt, {
			arg busIn = 0, gain = 1, urLag = 1, busOut = 0, busOTIAC = 1, levelOut = 0;
			var inMic, input, adaptCentroid, lowPass, highPass, ctrlLowPass, ctrlHighPass;

			inMic = LeakDC.ar(SoundIn.ar(busIn))*gain.lag3(0.5*urLag);
			input = Compander.ar(inMic, inMic);

			adaptCentroid = AdaptSpecCentroidNew.ar(input); //my adaptive spec centroid (it's a pseudo UGen)

			lowPass = LPF.ar(LPF.ar(LPF.ar(input, adaptCentroid), adaptCentroid), adaptCentroid);
			highPass = HPF.ar(HPF.ar(HPF.ar(input, adaptCentroid), adaptCentroid), adaptCentroid);

			ctrlLowPass = (1-EnvFollow.ar(lowPass)).abs;
			ctrlHighPass = (1-EnvFollow.ar(highPass)).abs;
			Out.ar([busOut, busOTIAC], lowPass*ctrlLowPass*levelOut.lag3(0.1) );
			Out.ar([busOut, busOTIAC], highPass*ctrlHighPass*levelOut.lag3(0.1) );
		}).add;

		// basic mixer synth
		SynthDef(\otiac_mixer, { arg levelTransducer = 0, levelSegments = 0, levelRoom = 0, outMic = 0, outTransducer = 0, outL = 0, gate = 1;
			var transducer, room, control, cutoffEnv;
			transducer  = Mix.new([In.ar(~outGuitar), In.ar(~outSegments)*levelSegments.lag3(0.1)]);
			room = LeakDC.ar(In.ar(outMic));
			control = (1-EnvFollow.ar(transducer)).abs;
			cutoffEnv = EnvGen.kr(Env.cutoff(1), gate, doneAction: Done.freeSelf);

			Out.ar(outTransducer, Limiter.ar(transducer*control*cutoffEnv)*levelTransducer.lag3(0.1));
			Out.ar(outL, GVerb.ar(Limiter.ar(room*cutoffEnv)*levelRoom.lag3(0.1)));
		}).add;

		// just a synth that I use as a digital timer
		SynthDef(\timerSynth, {
			var minutes, seconds;
			minutes = Sweep.kr(Impulse.kr(0), 60.reciprocal).floor(1);
			seconds = Sweep.kr(Impulse.kr(0)).round(0.1)-(60*minutes);
			SendReply.kr(Impulse.kr(100), '/timerSeconds', seconds.round(0.1));
			SendReply.kr(Impulse.kr(100), '/timerMinutes', minutes);
		}).add;

	}
}