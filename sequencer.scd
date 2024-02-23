(
s.boot;

w = Window(bounds:Rect(200, 200, 1000, 1000)).front;

w.acceptsMouseOver = true;

~block = SequencerBlock(4, 8,
	scale: 2.pow([0, 1, 3, 4, 6, 8, 9, 11, 13, 14, 16, 17, 19, 21, 22, 24, 26, 27, 29, 31]/31),
	color: [
		[Color.gray(0, 0.4), Color.gray(0.4, 0.4), Color.blue(1, 0.3)],
		[0, 20], [2, 3, 5, 6, 8, 10, 11, 13, 14, 16, 17, 19], [1, 4, 7, 9, 12, 15, 18]
	],
	tempo: 20
);

x = Sequencer(w, ~block);
x.init();
)