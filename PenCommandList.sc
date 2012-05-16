

PenCommandList {

	var <list;

	add { arg selector ... args;
		list = list.add( [selector] ++ args );
		// returns index of the command just inserted
		// so the command can be manipulated later
		^list.size - 1
	}
	clear {
		list = nil;
	}
	value {
		var pen;
		pen = GUI.pen;
		pen.use {
			list.do { arg args; pen.perform(*args) }
		}
	}
}


