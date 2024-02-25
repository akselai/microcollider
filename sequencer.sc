SequencerNote {
	// on: start of the note in beats
	// dur: duration of the note in beats
	// pitch: log freq of the freq (equaves above standard)
	var <>on, <>pitch, <>dur;

	// var frame
	*new { | on, pitch, dur |
		^super.newCopyArgs(on, pitch, dur)
	}

	postln {
		("note on:" + this.on + ", relative pitch:" + this.pitch + ", dur:" + this.dur).postln
	}

	sample {
		var sampleNote = SequencerNote(0, this.pitch, 1);
		var noteBlock = SequencerBlock(1, 0.5, [sampleNote]);
		var sampleSound = SequencerPlayback(noteBlock, 0);
		sampleSound.play();
	}

	transpose { // move a note in time or in pitch

	}

	snap {

	}
}

SequencerBlock {
	// collection of SequencerNotes
	// essentially an array of SequencerNotes, but enhanced with its own class methods

	// new block e.g. when the scale/tempo changes
	var <>beatsPerBar, <>duration, <>notes, <>scale, <>color, <>tempo;
	var <>startingPoint, <>channelNumber;
	var <>isScaleChange, <>isTempoChange;
	var <>isDisplayable;
	// some blocks aren't displayable, like a selection of notes (abstracted into a list)
	// only the "notes" variable is meaningful in a selection

	*new { |beats, durat, notes, scale, color, tempo|
		(beats == nil).if{
			beats = 0;
		};
		(durat == nil).if{
			durat = 0;
		};
		(notes == nil).if{
			notes = [];
		};
		(scale == nil).if{ // contains unison for it to work properly as of yet
			scale = 2.pow((0..12)/12); // empty scale defaults to 12edo :spub:
		};
		(color == nil).if{
			color = [[Color.gray(0, 0.4), Color.gray(0.4, 0.4)], [0, scale.size], (1..(scale.size-1))];
		};
		(tempo == nil).if{
			tempo = 120; // seconds per beat (the default 120 is divided by 60 to get 2)
		};
		^super.newCopyArgs(beats, durat, notes, scale, color, tempo/60);
	}

	at {
		|num|
		^notes[num]
	}

	size {
		^notes.size
	}

	add { // add note at the end of the list
		|note, playSample = false|
		notes = notes.add(note);
		playSample.if{
			note.sample();
		};
	}

	removeAt { // remove note at position...
		|pos|
		notes.removeAt(pos);
	}

	transfer { // move all notes with indices in [this] to [that].
		// the number of notes between the two blocks should be conserved, not copying!
		|indices, that|
		var a = 0;
		indices = indices.sort;
		indices.do{
			|index|
			that = that.add(this[index - a]);
			this.removeAt(index - a);
			a = a + 1;
		};
	}

	/*
	mouseOn { // which note is the mouse on?
	|sequencer, mouseX, mouseY|
	}

	transpose { // move all notes together in time or in pitch
	|superBlock|
	NotYetImplementedError.throw;
	}

	moveMargin { // adjust all margins of notes
	|superBlock|
	NotYetImplementedError.throw;
	}

	snap { // snap all notes to the grid
	|superBlock|
	NotYetImplementedError.throw;
	}
	*/
}

SequencerTrack {
	// collection of SequencerBlocks
}

SequencerLabel {
	// on-screen labeling of elements.
	// examples: lyrics, chord symbols, "overtone" drawing of rooted chords, etc.
}

Sequencer {
	// visual elements
	var <>window, <>block; // currently can only support one block
	var selectionBlock; // block of the selection. useful when there are multiple of them.

	var windowIsFront = true;

	// screen elements

	var <view; // main view, currently can only support one main view
	var tickerView;

	var <blockScale;

	var <viewXScale = 0.25, <viewYScale = 0.3125, <rectYWidth = 24;
	var <displayXOffset = 0, <displayYOffset = -0.034;

	/* default values
	viewXScale = 1/4;
	viewYScale = 1/3.2;
	rectYWidth = 16; // in pixels
	displayXOffset = 0;
	displayYOffset = -1*viewYScale/7;
	*/

	// internal variables

	var freqLowest = 16, freqHighest = 4096;
	var baseFreq = 220;

	var mouseXPos = inf, mouseYPos = inf;
	var mouseXDiff, mouseYDiff;
	var mouseWhichRect = -1, mouseRectMargin = 0;
	var cursorDuration = 0.25;
	var <displayXQuant = 0.25; // snapMapping needs to read this

	var selectionIndices;

	var selectionXPos, selectionYPos; // upper left corner of selection

	// non-screen elements

	var timeSlider, pitchSlider;

	var playButton, pauseButton, stopButton;
	var addNoteButton, deleteNoteButton, editNotesButton, snapToGridButton;
	var saveButton, loadButton;
	var durationDialogBoxNum, durationDialogBoxDen, subbeatsPerBeatsDialogBox;

	// states

	var addingNotes = false, selectingNotes = false, editingNoteDurations = false;
	var ctrlPressed = false;

	// playback variables

	var playback;

	var tickerClock;

	var beatsPerBar, blockDuration;
	var playStartTime = nil;
	var playStartPosition = 0;
	var trackDuration = 40;


	*new { |w, block|
		^super.newCopyArgs(w, block);
	}

	init {
		//initialize the non-literal variables...

		view = UserView(window, Rect(50,50,900,600));
		tickerView = UserView(window, Rect(50,50,900,600));
		playback = SequencerPlayback(block, 0);

		beatsPerBar = block.beatsPerBar;
		blockDuration = block.duration;
		trackDuration = blockDuration;

		blockScale = block.scale;

		selectionBlock = SequencerBlock();
		selectionIndices = [];

		// rescale views on dragging window



		// draw the views
		// note that Pen can only be called within drawFunc!

		view.drawFunc = {
			this.updateStates();

			// draw the frame
			Pen.strokeColor = Color.black;
			Pen.width = 1.5;
			Pen.addRect(Rect(0, 0, view.bounds.width, view.bounds.height));
			Pen.stroke;

			// draw the time gridlines
			Pen.use{
				(beatsPerBar / viewXScale < 200).if{
					(beatsPerBar * blockDuration + 1).do{
						arg i;
						var xx = i / beatsPerBar;
						var lineXPos = Point(xx, 0).penMapping(this).x;

						(i % beatsPerBar == 0).if({
							Pen.strokeColor = Color.new(0, 0, 0, 0.4);
						}, {
							Pen.strokeColor = Color.new(0, 0, 0, 0.1);
						});
						Pen.line(
							Point(lineXPos, 0),
							Point(lineXPos, view.bounds.height)
						);
						Pen.stroke;
					};
				};
			};

			// draw the pitch gridlines

			9.do{
				arg i2;
				var i = i2-4;
				(blockScale.size).do{
					arg j;
					var yy = (log2(blockScale[j % blockScale.size]) + i);
					var coloring = block.color;
					var lineYPos = Point(0, yy).penMapping(this).y;

					coloring[0].size.do{
						arg i;
						(coloring[i+1].includes(j)).if{
							Pen.strokeColor = coloring[0][i];
						}
					};

					Pen.line(
						Point(0, lineYPos),
						Point(view.bounds.width, lineYPos)
					);
					Pen.stroke;
				}
			};

			// draw the note rectangles
			block.notes.size.do{
				arg i;
				var note = block.notes[i];
				var rectPoint = Point(note.on, note.pitch).penMapping(this);
				var frameDur = note.dur * view.bounds.width * viewXScale;
				(i == mouseWhichRect).if({
					Pen.fillColor = Color.yellow;
				}, {
					Pen.fillColor = Color.grey;
				});

				Pen.addRect(
					Rect.fromPoints(
						Point(rectPoint.x, rectPoint.y - (rectYWidth / 3)),
						Point(rectPoint.x + frameDur, rectPoint.y + (rectYWidth / 3)) // no idea why it's 3 and not 2
					)
				);
				Pen.fill;

				editingNoteDurations.if({ // edit duration extenders
					var l = (note.dur * viewXScale).editRectMarginLength(rectYWidth / view.bounds.width) * view.bounds.width;
					Pen.fillColor = Color.new(1, 0, 0, 0.4);
					Pen.addRect(
						Rect.fromPoints(
							Point(rectPoint.x, rectPoint.y - (rectYWidth / 3)),
							Point(rectPoint.x + l, rectPoint.y + (rectYWidth / 3))
						)
					);
					Pen.addRect(
						Rect.fromPoints(
							Point(rectPoint.x + (frameDur - l), rectPoint.y - (rectYWidth / 3)),
							Point(rectPoint.x + frameDur, rectPoint.y + (rectYWidth / 3))
						)
					);
					Pen.fill;
				});
			};

			// draw the rectangle cursor

			(true).if {
				var snapPoint = Point(mouseXPos, mouseYPos).snapMapping(this);
				var lengthInPixels = cursorDuration * viewXScale * view.bounds.width;
				Pen.fillColor = Color.new(0, 0.8, 0.8, 0.4);
				Pen.addRect(
					Rect.fromPoints(
						Point(snapPoint.x, snapPoint.y - (rectYWidth / 3)),
						Point(snapPoint.x + lengthInPixels, snapPoint.y + (rectYWidth / 3))
					)
				);
				Pen.fill;
			};

			// move the time and pitch sliders
			(true).if {
				var upperRange = log2(freqHighest / baseFreq);
				var lowerRange = log2(baseFreq / freqLowest);
				var totalRange = upperRange + lowerRange;

				timeSlider.value = displayXOffset / (trackDuration * viewXScale - 1);
				timeSlider.thumbSize = view.bounds.width / viewXScale / trackDuration;

				//displayXOffset = timeSlider.value * (trackDuration * viewXScale - 1);
				//displayYOffset = pitchSlider.value * (totalRange * viewYScale - 1) - (lowerRange * viewYScale);

				pitchSlider.value = (displayYOffset + (lowerRange * viewYScale)) / (totalRange * viewYScale - 1);
				pitchSlider.thumbSize = view.bounds.height / viewYScale / totalRange;
			};

			// selections

			(selectingNotes).if{
				selectionIndices = [];

				not(selectionXPos.isNil || selectionYPos.isNil || mouseXPos.isNil || mouseYPos.isNil).if{
					var upperLeft = Point(selectionXPos, selectionYPos).scaledPenMapping(this);
					var lowerRight = Point(mouseXPos, mouseYPos).scaledPenMapping(this);
					var selectionRect = Rect.fromPoints(upperLeft, lowerRight);

					// draw selection rectangle

					Pen.fillColor = Color.new(0, 0.8, 0.8, 0.2);
					Pen.addRect(selectionRect);
					Pen.fill;

					block.notes.size.do{ // find selection matches
						arg i;
						var note = block.notes[i];
						var rectPoint = Point(note.on, note.pitch).penMapping(this);
						var frameDur = note.dur * view.bounds.width * viewXScale;

						var compare = Rect.fromPoints(
							Point(rectPoint.x, rectPoint.y - (rectYWidth / 3)),
							Point(rectPoint.x + frameDur, rectPoint.y + (rectYWidth / 3)) // no idea why it's 3 and not 2
						);

						selectionRect.intersects(compare).if{
							selectionIndices = selectionIndices.add(i);

							Pen.fillColor = Color.yellow;
							Pen.addRect(compare);
							Pen.fill;
						}
					};
				};
			};

			// draw the selection
			selectionBlock.notes.size.do{
				arg i;
				var note = selectionBlock.notes[i];
				var rectPoint = Point(note.on, note.pitch).penMapping(this);
				var frameDur = note.dur * view.bounds.width * viewXScale;

				Pen.fillColor = Color.yellow;

				Pen.addRect(
					Rect.fromPoints(
						Point(rectPoint.x, rectPoint.y - (rectYWidth / 3)),
						Point(rectPoint.x + frameDur, rectPoint.y + (rectYWidth / 3))
					)
				);
				Pen.fill;

				editingNoteDurations.if({ // edit duration extenders
					var l = (note.dur * viewXScale).editRectMarginLength(rectYWidth / view.bounds.width) * view.bounds.width;
					Pen.fillColor = Color.new(1, 0, 0, 0.4);
					Pen.addRect(
						Rect.fromPoints(
							Point(rectPoint.x, rectPoint.y - (rectYWidth / 3)),
							Point(rectPoint.x + l, rectPoint.y + (rectYWidth / 3))
						)
					);
					Pen.addRect(
						Rect.fromPoints(
							Point(rectPoint.x + (frameDur - l), rectPoint.y - (rectYWidth / 3)),
							Point(rectPoint.x + frameDur, rectPoint.y + (rectYWidth / 3))
						)
					);
					Pen.fill;
				});
			};
		};

		view.action = {view.refresh};

		view.mouseDownAction = { arg view, x = 0.5, y, m, c;
			var normalizedRectYWidth = rectYWidth / view.bounds.width;
			var withinRect = {
				arg x, y, rx, ry, lx, ly;
				(rx <= x) && (x <= (rx + lx)) && (ry <= y) && (y <= (ry + ly))
			};
			var xclick = (x).linlin(0, view.bounds.width, 0, 1) + displayXOffset;
			var yclick = 1 - (y).linlin(0, view.bounds.height, 0, 1) + displayYOffset;
			var i = 0;
			([0].includes(m) && (block.notes.size != 0)).if{ // there is more than 0 blocks on the screen
				var note = block.notes[i]; // find index of selected note
				while({
					(i < block.notes.size) &&
					not(withinRect.value(xclick, yclick,
						note.on * viewXScale, (note.pitch * viewYScale - (normalizedRectYWidth * 1/2)),
						note.dur * viewXScale, normalizedRectYWidth
					))
				}, {
					note = block.notes[i];
					i = i + 1;
				});

				(i == block.notes.size).if{i = -1}; // end of search

				(i > 0).if{i = i - 1};

				(i != -1).if({
					var note = block.notes[i];
					var l = (note.dur * viewXScale).editRectMarginLength(normalizedRectYWidth);
					mouseWhichRect = i;
					mouseXDiff = xclick - (note.on * viewXScale);
					mouseYDiff = yclick - (note.pitch * viewYScale);

					selectingNotes = false;
					selectionXPos = xclick;
					selectionYPos = yclick;

					// find dragged margin

					mouseRectMargin = 0; // none (center)

					withinRect.value(xclick, yclick,
						note.on * viewXScale, note.pitch * viewYScale - (normalizedRectYWidth * 1/2),
						l, normalizedRectYWidth
					).if{
						mouseRectMargin = -1; // left
					};
					withinRect.value(xclick, yclick,
						(note.on + note.dur) * viewXScale - l, note.pitch * viewYScale - (normalizedRectYWidth * 1/2),
						l, normalizedRectYWidth
					).if{
						mouseRectMargin = 1; // right
					};

					note.sample();
				}, {
					mouseWhichRect = -1; // not dragging a block, maybe selecting blocks?
					not(addingNotes).if{
						selectingNotes = true;
						selectionXPos = xclick;
						selectionYPos = yclick;
					}
				});
			};

			((mouseWhichRect == -1) && (selectionBlock.size != 0)).if({
				// when something is selected, but nothing is done, free selection
				selectionBlock.transfer((0..(selectionBlock.size - 1)), block);
			}, {
				// when something is selected, and a block is dragged, move all blocks by the same amount
				// see mouseMoveAction
			});

			([0].includes(m) && (block.notes.size == 0)).if{ // there are no blocks to select, but you can still "select"
				mouseWhichRect = -1;
				not(addingNotes).if{
					selectingNotes = true;
					selectionXPos = xclick;
					selectionYPos = yclick;
				}
			};

			view.refresh;
		};

		view.mouseOverAction = {
			|view, x = 0.5, y|
			windowIsFront.if{
				mouseXPos = (x).linlin(0, view.bounds.width, 0, 1) + displayXOffset;
				mouseYPos = 1 - (y).linlin(0, view.bounds.height, 0, 1) + displayYOffset;
				view.refresh;
			}
		};

		view.mouseMoveAction = {
			|view, x = 0.5, y, m, c|
			var xclick, yclick;
			var toneMod;
			xclick = (x).linlin(0, view.bounds.width, 0, 1) + displayXOffset;
			yclick = 1 - (y).linlin(0, view.bounds.height, 0, 1) + displayYOffset;
			mouseXPos = xclick;
			mouseYPos = yclick;

			// dragging a rectangle
			([0].includes(m) && (mouseWhichRect >= 0)).if{
				var note = block.notes[mouseWhichRect];
				case
				{mouseRectMargin == -1 && editingNoteDurations} { // left
					var xx = note.on + note.dur;
					note.on = (xclick / viewXScale).trunc(displayXQuant).clip(-inf, xx);
					note.dur = (xx - note.on);
				}
				{mouseRectMargin == 1 && editingNoteDurations} { // right
					note.dur = (xclick / viewXScale - note.on).trunc(displayXQuant).clip(0, inf);
				}
				{ // center
					var xx, yy;
					xx = ((xclick - mouseXDiff) / viewXScale).trunc(displayXQuant);
					yy = ((yclick - mouseYDiff) / viewYScale).snap(blockScale);
					note.on = xx;  note.pitch = yy;
				}
			};

			// dragging a selection
			(selectionBlock.size != 0 && (mouseWhichRect >= 0)).if{
				var pivotNote = block.notes[mouseWhichRect];
				case
				{mouseRectMargin == -1 && editingNoteDurations} { // left
					// TODO: put something here!!!
				}
				{mouseRectMargin == 1 && editingNoteDurations} { // right
					// TODO: put something here!!!
				}
				{ // center
					var origX, origY, newX, newY;
					origX = selectionXPos;
					origY = selectionYPos;
					newX = ((xclick - mouseXDiff) / viewXScale).trunc(displayXQuant);
					newY = ((yclick - mouseYDiff) / viewYScale).snap(blockScale);
					// note.on = xx;  note.pitch = yy;
					\beep.postln;
					origX.postln;
					origY.postln;
					newX.postln;
					newY.postln;
				}
			};
			view.doAction;
		};

		view.mouseUpAction = {
			(mouseWhichRect == -1 && addingNotes).if({ // add note
				var xx, yy;
				xx = (mouseXPos / viewXScale).trunc(displayXQuant);
				yy = mouseYPos / viewYScale;
				yy = yy.snap(blockScale);
				block.add(SequencerNote(xx, yy, cursorDuration), true);
			});

			// transfer selection to block
			selectionBlock = SequencerBlock();
			block.transfer(selectionIndices, selectionBlock);
			selectingNotes = false; // free the selection

			view.refresh;
			window.refresh;
		};

		view.keyDownAction = {
			|doc, char, mod, unicode, keycode, key|
			(key == 16777249).if{
				ctrlPressed = true;
			};
			(key == 16777223).if{
				this.deleteNote();
			};
		};

		view.keyUpAction = {
			|doc, char, mod, unicode, keycode, key|
			(key == 16777249).if{
				ctrlPressed = false;
			};
		};

		view.mouseWheelAction = {
			arg view, x, y, modifiers, xDelta, yDelta;
			var mouseXPos, t1, t2, factor = 1.1.pow(yDelta/30);
			windowIsFront.if{
				mouseXPos = (x).linlin(0, view.bounds.width, 0, 1) + displayXOffset;
				mouseYPos = 1 - (y).linlin(0, view.bounds.height, 0, 1) + displayYOffset;
				ctrlPressed.if({
					var upperRange = log2(freqHighest / baseFreq);
					var lowerRange = log2(baseFreq / freqLowest);
					var totalRange = upperRange + lowerRange;

					t1 = mouseYPos / viewYScale;
					viewYScale = viewYScale * factor;
					viewYScale = viewYScale.clip(1/totalRange, inf);
					t2 = mouseYPos / viewYScale;
					displayYOffset = displayYOffset + ((t1 - t2) * viewYScale);
					displayYOffset = displayYOffset.clip(-1 * lowerRange * viewYScale, upperRange * viewYScale - 1);
				}, {
					t1 = mouseXPos / viewXScale;
					viewXScale = viewXScale * factor;
					viewXScale = viewXScale.clip(1/trackDuration, inf);
					t2 = mouseXPos / viewXScale;
					displayXOffset = displayXOffset + ((t1 - t2) * viewXScale);
					displayXOffset = displayXOffset.clip(0, trackDuration * viewXScale - 1);
				});

				view.mouseMoveAction;
				view.refresh;
			}
		};

		tickerView.acceptsMouse = false;

		tickerView.drawFunc = {
			Pen.use {
				Pen.translate(0, view.bounds.height);
				Pen.scale(view.bounds.width, -1 * view.bounds.height);
				Pen.width = 2.0/view.bounds.width;

				Pen.translate(-1 * displayXOffset, -1 * displayYOffset);

				(not(playback.playStartTime.isNil)).if {
					var pos;
					Pen.strokeColor = Color.new(1, 0, 0, 0.4);
					pos = (thisThread.seconds - playback.playStartTime) * viewXScale * block.tempo;
					Pen.line(
						Point(pos, -10),
						Point(pos, 10)
					);
					Pen.stroke;
				};
			}
		};

		tickerView.action = {tickerView.refresh};

		timeSlider = Slider(window, Rect(50, 650, 900, 20));
		timeSlider.step = 0.01;
		timeSlider.action_({
			displayXOffset = timeSlider.value * (trackDuration * viewXScale - 1);
			view.refresh;
		});

		pitchSlider = Slider(window, Rect(950, 50, 20, 600));
		pitchSlider.step = 0.01;
		pitchSlider.action_({
			var upperRange = log2(freqHighest / baseFreq);
			var lowerRange = log2(baseFreq / freqLowest);
			var totalRange = upperRange + lowerRange;

			displayYOffset = pitchSlider.value * (totalRange * viewYScale - 1) - (lowerRange * viewYScale);
			view.refresh;
		});

		playButton = Button(window, Rect(50, 670, 80, 40));
		playButton.states_([["play", Color.black]]);
		playButton.mouseDownAction_({
			playback.play();
			this.scrollTicker();
			view.refresh;
			window.refresh;
		});
		/*
		pauseButton = Button(window, Rect(50, 710, 80, 40));
		pauseButton.states_([["pause", Color.black]]);
		pauseButton.mouseDownAction_({
		playback.stop();
		this.stopTicker();
		view.refresh;
		window.refresh;
		});
		*/
		stopButton = Button(window, Rect(50, 710, 80, 40));
		stopButton.states_([["stop", Color.black]]);
		stopButton.mouseDownAction_({
			playback.stop();
			this.stopTicker();
			view.refresh;
			window.refresh;
		});

		addNoteButton = Button(window, Rect(150, 670, 120, 40));
		addNoteButton.states_([
			["editing notes", Color.black],
			["adding notes", Color.black],
		]);
		addNoteButton.mouseDownAction_({
			addingNotes = true;
			view.refresh;
		});

		deleteNoteButton = Button(window, Rect(150, 710, 120, 40));
		deleteNoteButton.states_([["delete note", Color.black]]);
		deleteNoteButton.mouseDownAction_({
			this.deleteNote();
		});

		editNotesButton = Button(window, Rect(280, 670, 200, 40));
		editNotesButton.states_([
			["edit notes", Color.black],
			["edit note duration", Color.black],
			// ["build chord", Color.black]
		]).action_({ arg button;
			window.refresh;
			view.refresh;
		});

		editNotesButton.mouseDownAction_({
			window.refresh;
			view.refresh;
		});

		snapToGridButton = Button(window, Rect(280, 740, 200, 40));
		snapToGridButton.states_([
			["snap to grid", Color.black],
			["free", Color.black],
			// ["build chord", Color.black]
		]).action_({ arg button;
			window.refresh;
			view.refresh;
		});

		durationDialogBoxNum = NumberBox(window, Rect(455, 715, 45, 40));
		~durationDialogBoxNumerator = 1;
		durationDialogBoxNum.value = 1;
		durationDialogBoxNum.action = {
			arg nn;
			~durationDialogBoxNumerator = nn.value;
			cursorDuration = ~durationDialogBoxNumerator / ~durationDialogBoxDenominator;
		};

		durationDialogBoxDen = NumberBox(window, Rect(505, 715, 45, 40));
		~durationDialogBoxDenominator = 8;
		durationDialogBoxDen.value = 8;
		durationDialogBoxDen.action = {
			arg dd;
			~durationDialogBoxDenominator = dd.value;
			cursorDuration = ~durationDialogBoxNumerator / ~durationDialogBoxDenominator;
		};

		subbeatsPerBeatsDialogBox = NumberBox(window, Rect(555, 715, 45, 40));
		subbeatsPerBeatsDialogBox.value = 4;
		subbeatsPerBeatsDialogBox.action = {
			arg nn;
			cursorDuration = 1 / nn.value / beatsPerBar;
			displayXQuant = 1 / nn.value / beatsPerBar;
		};
	}

	updateStates {
		// view.bounds = Rect(50, 50, window.bounds.width - 100, min(600, window.bounds.height - 100));

		window.toFrontAction = {
			windowIsFront = true;
		};

		window.endFrontAction = {
			windowIsFront = false;
		};

		(addNoteButton.value == 1).if({
			addingNotes = true;
			selectingNotes = false;
		}, {
			addingNotes = false;
		});

		(editNotesButton.value == 1).if({ // adjust note duration
			editingNoteDurations = true;
		}, {
			editingNoteDurations = false;
		});

		(snapToGridButton.value == 1).if({
			displayXQuant = 0;
		}, {
			displayXQuant = 1 / subbeatsPerBeatsDialogBox.value / beatsPerBar;
		});
	}

	scrollTicker {
		tickerClock = AppClock.sched(0, { arg time;
			tickerView.refresh;
			0.02;
		});
	}

	stopTicker {
		tickerClock.clear;
	}

	deleteNote {
		(mouseWhichRect >= 0).if {
			block.removeAt(mouseWhichRect);
			mouseWhichRect = -1;
			view.refresh;
		}
	}
}

SequencerPlayback {
	var block, playStartPosition, synth;
	var <playStartTime = nil;
	var playSequence;

	var baseFreq = 110;

	*new {
		|block, startpos = 0|
		^super.newCopyArgs(block, startpos);
	}

	init {
		SynthDef(\lrsaw, {
			arg freq = 440, sus = 0.1, amp = 0.3;
			var signal = 0, signal_left = 0, signal_right = 0, env, rel, rotator;
			signal_left = LPF.ar(Saw.ar(freq), XLine.ar(min(freq * 20, 10000), min(freq * 5, 10000), 0.5));
			signal_right = DelayN.ar(signal_left, 1/2/freq, 1/2/freq);
			signal_left = LPF.ar(signal_left, XLine.ar(min(freq * 20, 10000), min(freq * 5, 10000), 0.5));
			signal_right = LPF.ar(signal_right, XLine.ar(min(freq * 20, 10000), min(freq * 5, 10000), 0.5));
			rotator = LFSaw.kr(2);
			rel = min(sus/3, 0.3);
			env = Env.linen(0.01, sus, rel, amp, \lin);
			signal = Rotate2.ar(signal_left, signal_right, rotator) * EnvGen.kr(env, doneAction:2);
			Out.ar(0, signal);
		}).add;
	}

	play {
		var isAbsoluteTime; // in beats or in seconds?

		this.init;
		playSequence.clear;
		playStartTime = thisThread.seconds;

		block.size.do{
			arg i;
			var note, scrollSpeed;
			note = block[i];
			scrollSpeed = block.tempo;
			((note.on - playStartPosition) / scrollSpeed >= 0).if{
				playSequence = SystemClock.sched(note.on / scrollSpeed, { arg time;
					var freq = 2.pow(note.pitch) * baseFreq / 2;
					(instrument: \lrsaw, freq: freq, sus: note.dur / scrollSpeed).play;
				});
			}
		};
	}

	pause {
		NotYetImplementedError.throw;
	}

	stop {
		playStartTime = -inf;
		playSequence.clear;
	}
}

SequencerInterval {
	var <num, <den, <cents;
	var <monzo, <subgroup;
	var <isFraction, <isCents, <isMonzo;

	*new {
		|num, den|
		var cents, monzo, subgroup;
		den.isNil.if{
			den = 1
		};

		// if reducible, reduce.
		num = num.div(gcd(num, den));
		den = den.div(gcd(num, den));

		// convert to cents. easy!
		cents = log(num/den) * 1731.2340490668;

		// convert to monzo. hard...
		monzo = []; subgroup = [];
		num.factors.do{
			|n|
			subgroup.includes(n).if({
				var j = subgroup.indexOf(n);
				monzo[j] = monzo[j] + 1;
			}, {
				subgroup = subgroup.add(n);
				monzo = monzo.add(1);
			});
		};
		den.factors.do{
			|n|
			subgroup.includes(n).if({
				var j = subgroup.indexOf(n);
				monzo[j] = monzo[j] - 1;
			}, {
				subgroup = subgroup.add(n);
				monzo = monzo.add(-1);
			});
		};

		^super.newCopyArgs(num, den, cents, monzo, subgroup);
	}

	*fromMonzo {
		|monzo, subgroup|
		^super.newCopyArgs(nil, nil, nil, monzo, subgroup);
	}

	isInterval { ^true }

	// because of possible integer overflow, everything is done in terms of monzos. else, in cents.

	+ { // adding vectors, which is multiplying freq ratios
		|that|
		var combineSubgroups = this.subgroup.asSet | that.subgroup.asSet;
		var resultMonzo = [], resultSubgroup = [];
		combineSubgroups = combineSubgroups.asArray.sort;
		combineSubgroups.do{
			|prime|
			(this.subgroup.includes(prime) && that.subgroup.includes(prime)).if({
				var i = this.subgroup.indexOf(prime);
				var j = that.subgroup.indexOf(prime);
				var sum = this.monzo[i] + that.monzo[j];
				(sum != 0).if{
					resultMonzo = resultMonzo.add(sum);
					resultSubgroup = resultSubgroup.add(prime);
				}
			}, {
				(this.subgroup.includes(prime)).if{
					var i = this.subgroup.indexOf(prime);
					resultMonzo = resultMonzo.add(this.monzo[i]);
					resultSubgroup = resultSubgroup.add(prime);
				};
				(that.subgroup.includes(prime)).if{
					var j = that.subgroup.indexOf(prime);
					resultMonzo = resultMonzo.add(that.monzo[j]);
					resultSubgroup = resultSubgroup.add(prime);
				};
			});
		};
		^SequencerInterval.fromMonzo(resultMonzo, resultSubgroup)
	}

	- { // subtracting vectors, which is dividing freq ratios
		|that|
		^this + (that * (-1))
	}

	* { // multiplying vectors by integer scalar, which is exponentiating freq ratios
		|number|
		number.isInteger.if({
			^SequencerInterval.fromMonzo(this.monzo * number, this.subgroup)
		}, {
			error("binary operator '*' failed, attempt to multiply monzo by a non-integer: %".format(number));
			this.dump;
			this.dumpBackTrace;
			this.halt;
		})
	}

	+/ { // mediant (direct sum), (a/b) +/ (c/d) = (a+c)/(b+d)
		|that|
		NotYetImplementedError.throw;
	}

	asFraction {
		// compute fraction representation of monzo if no integer overflow
		// return true if possible, false otherwise
		var a = 1, b = 1, a_int = 1.0, b_int = 1.0;
		monzo.size.do{
			|i|
			(monzo[i] > 0).if{
				a_int = a_int * subgroup[i].pow(monzo[i]);
				a = (a + 0.0) * subgroup[i].pow(monzo[i]);
			};
			(monzo[i] < 0).if{
				b_int = b_int * subgroup[i].pow(monzo[i].neg);
				b = (b + 0.0) * subgroup[i].pow(monzo[i].neg);
			};
		};
		((a <= 2147483647) && (b <= 2147483647)).if({
			num = a_int;
			den = b_int;
			^true;
		}, {
			^false;
		});
	}

	asCents {
		NotYetImplementedError.throw;
	}

	postln {
		NotYetImplementedError.throw;
		// if no integer overflow as a fraction, print a fraction.
		// otherwise, print a monzo.
		// (this.num ++ "/" ++ this.den).postln;
	}
}

SequencerScale {
	// collection of SequencerIntervals
	var <intervals;
	var <>colors;

	*new {
		|intervals, colors|
		colors.isNil.if{
			colors = Color.gray(0.5, 1.0)!intervals.size;
		};
		^super.newCopyArgs(intervals, colors);
	}

	*fromScala {
		|invls|
		NotYetImplementedError.throw;
	}

	sort {
		NotYetImplementedError.throw;
	}

	reduce { // last element is equave
		NotYetImplementedError.throw;
	}
}

+ Float {
	editRectMarginLength {
		|rectYWidth|
		(this.value >= (rectYWidth * 3)).if({
			^rectYWidth;
		}, {
			^(this.value * 1/3);
		});
	}

	snap { // x = number of equaves
		arg scale;
		var x = this.value, eq = scale[scale.size - 1], integerPart, fractionPart;
		integerPart = x.floor();
		fractionPart = eq.pow(x.frac()).nearestInList(scale).log() / eq.log();
		^(integerPart + fractionPart)
	}
}

+ Point {
	penMapping { // mapping points: canvas coordinates (full length = 1) -> # of pixels
		|seq|
		^Point(
			seq.view.bounds.width * (this.x * seq.viewXScale - seq.displayXOffset),
			seq.view.bounds.height * (1 - (this.y * seq.viewYScale) + seq.displayYOffset)
		);
	}

	scaledPenMapping { // mapping points: canvas coordinates (full length = 1) -> map coordinates (full length = X/YScale)
		|seq|
		^Point(
			seq.view.bounds.width * (this.x - seq.displayXOffset),
			seq.view.bounds.height * (1 - this.y + seq.displayYOffset)
		);
	}

	snapMapping { // mapping points to scale, snapped to grid (with the above coords)
		|seq|
		var x_ = seq.viewXScale;
		var y_ = seq.viewYScale;
		^Point(
			seq.view.bounds.width * ((this.x / x_).trunc(seq.displayXQuant) * x_ - seq.displayXOffset),
			seq.view.bounds.height * (1 - ((this.y / y_).snap(seq.blockScale) * y_) + seq.displayYOffset)
		);
	}
}

// TODO: make inverse methods and replace seperate X/Y coordinates with Points