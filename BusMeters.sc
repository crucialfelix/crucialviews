

BusMeters {

	classvar serverMeterViews, 	updateFreq = 2, dBLow = -86.0,dbHigh = 0.0;

	var <server,<>busses;
	var responder, synth, <meters, <peaks,<peaksG;
	var numRMSSamps, numRMSSampsRecip,sones;

	*new { |server,busses|
		^super.new.init(server,busses)
	}

	init { arg aserver, abusses;
		server = aserver ?? {Server.default};
		busses = abusses ?? {[Bus(\audio,0,2,server)]};
		meters = Array.newClear(busses.size);
		sones = Array.newClear(busses.size);
		peaks = Array.fill(busses.size,{dBLow});
		peaksG = Array.newClear(busses.size);
		numRMSSamps = (server.sampleRate ? server.options.sampleRate ? 44100.0) / updateFreq;
		numRMSSampsRecip = 1 / numRMSSamps;
	}
	gui { arg parent,bounds;
		var meterWidth,meterHeight;
		if(bounds.isNil,{
			meterWidth = 30;
			meterHeight = 130;
		},{
			meterWidth = bounds.width.asFloat / busses.size;
			meterHeight = bounds.height;
		});		
		parent = parent ?? {FlowView(nil,bounds ?? {Rect(0,0,busses.size * (meterWidth + 4),meterHeight + 20)})};
		busses.do { arg b,i;
			parent.comp({ arg v;
				this.makePeak(i,v,Rect(0,0,meterWidth,GUI.skin.buttonHeight));
				this.makeBusMeter(i,v,Rect(0,GUI.skin.buttonHeight,meterWidth,meterHeight))
			},
			meterWidth@(meterHeight + GUI.skin.buttonHeight)
			).background_(Color.white);
		};
		
		parent.onClose = { this.stop };
	}
	makeBusMeter { arg bi,layout,bounds;
		var bv,pk,so;
		bounds = bounds ?? {Rect(0,0,30,180)};
		bv = LevelsView(layout,bounds);
		meters.put(bi,bv);
		^bv
	}
	makePeak { arg bi,layout,bounds;
		var pk;
		bounds = bounds ?? {Rect(0,0,30,GUI.skin.buttonHeight)};
		pk = ActionButton(layout,dBLow.round(0.1).asString,{
			peaks.put(bi,dBLow);
			pk.label_(dBLow.round(0.1).asString).refresh;
		},bounds.width,bounds.height);
		peaks.put(bi,dBLow);
		peaksG.put(bi,pk);
		^pk
	}		

	start {
		if(synth.isPlaying.not,{
			server.waitForBoot({this.prStart});
		})
	}
	prStart {
		var name;
		name = "BusMeters".catList(busses.collect({ arg bus; bus.index }));
		synth = SynthDef(name, {
			var ins, imp,reset,sones,fft,b;
			ins = busses.collect({ arg b; In.ar(b.index,b.numChannels) });
			imp = Impulse.ar(updateFreq);
			reset = Delay1.ar(imp);
			SendReply.ar(imp, "/" ++ name,
				ins.collect({ arg in,i; 
					var fft,sones,b;
					b = LocalBuf(1024, 1);
					fft = FFT(b,Normalizer.ar(Mono(in),0.5));
					sones = Loudness.kr(fft);
					[
						RunningSum.ar(in.squared, numRMSSamps),
						Peak.ar(in, reset).lag(0, 2),
						Peak.kr(sones,reset).lag(0,1)
					]
				}).flat
			);
		}).play(RootNode(server), nil, \addToTail);

		responder = OSCresponderNode(server.addr, "/" ++ name, { |t, r, msg|
			{
				try {
					if(meters[0].isClosed.not,{
						meters.do { arg m,i;
							var val,peak,sones,fi;
							fi = i * 5;
							val = msg[ [3,4] + fi ];
							peak = msg[ [5,6] + fi ];
							sones = msg[ 7 + fi ];

							peak = peak.ampdb;
							m.setValues(
								peak,
								(val.max(dbHigh) * numRMSSampsRecip).sqrt.ampdb,
								sones,
								peak = peak.maxItem
							);
							peaks.put(i, max(peaks.at(i),peak) );
							if(peaksG.at(i).notNil,{
								peaksG.at(i).label_(peaks.at(i).round(0.1).asString).refresh;
							})
						}
					})
				}
			}.defer;
		}).add;
	}	
		
	stop {
		if(synth.isPlaying,{
			synth.free
		});
		responder.remove;
	}
	remove { this.stop }
}


LevelsView {

	var <>dbLow = -86.0, <>dbHigh = 0.0, <>bounds;
	var pcl;
	var view;
	var al, ar, pl, pr, srect, peaki, avgi;

	*new { arg parent,bounds;
		^super.new.init(parent,bounds)
	}

	init { arg layout,b;
		var font, halfwidth;

		layout = layout ?? { FlowView.new };
		bounds = b ?? { Rect(0,0,20,100) };
		halfwidth = bounds.width / 2 - 1;
		pcl = PenCommandList.new;		
		view = UserView(layout,bounds);
		view.drawFunc = {pcl.value};

		bounds = view.bounds.moveTo(0,0);
		font = Font.sansSerif(9);

		// background
		pcl.add( \fillColor_ , Color(0.86274509803922, 0.85882352941176, 0.84705882352941) );
		pcl.add( \fillRect, bounds );

		// averages
		pcl.add( \fillColor_, Color(0.0, 0.86274509803922, 0.29019607843137) );
		ar = Rect.fromPoints( 0@bounds.bottom,halfwidth@bounds.top);
		pcl.add( \fillRect, ar );
		al = Rect.fromPoints( (halfwidth + 1)@bounds.bottom,bounds.width@bounds.top);
		pcl.add( \fillRect, al);

		// peak
		pcl.add( \fillColor_, Color.red );
		pl = Rect( 0,bounds.bottom,halfwidth,1);
		pcl.add( \fillRect, pl );
		pr = Rect( halfwidth + 1,bounds.bottom,halfwidth,1);
		pcl.add( \fillRect, pr );
		peaki = pcl.add( \stringCenteredIn, "", Rect(0,bounds.top,bounds.width,20), font, Color.red  );
		
		// normalized sones
		pcl.add( \fillColor_, Color.blue(alpha:0.5) );
		srect = Rect(0,bounds.bottom,bounds.width,4);
		pcl.add( \fillRect, srect );

		// average number at half way
		avgi = pcl.add( \stringCenteredIn, "", Rect(0,bounds.height / 2,bounds.width,20), font, Color.black  );

		view.refresh;
	}
	setValues { arg peakLevels,avgs,sones,maxPeak;
		
		al.top = this.scaleLevel(avgs[0]);
		ar.top = this.scaleLevel(avgs[1]);

		pl.top = this.scaleLevel(peakLevels[0]);
		pr.top = this.scaleLevel(peakLevels[1]);

		srect.top = sones.linlin(0,60,bounds.bottom,bounds.top);

		pcl.list.at(peaki).put(1, maxPeak.round(0.1).asString );
		pcl.list.at(avgi).put(1, avgs.maxItem.round.asString );
		view.refresh;
	}
	scaleLevel { arg l;
		^l.linlin(dbLow,dbHigh,bounds.bottom,bounds.top)
	}
	isClosed { ^view.isClosed }
	draw {
		pcl.value
	}
}


