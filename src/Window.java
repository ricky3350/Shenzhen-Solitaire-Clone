import java.awt.Point;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Window extends Application {

	private static final int TRANSLATE_DURATION = 250;

	private Game game;

	private StackPane stackPane;
	private NumberBinding cardWidth;
	private NumberBinding xOffset;

	private final BidirectionalHashMap<Point, Card> cards = new BidirectionalHashMap<>();
	private boolean animating = false;
	private final Set<Animation> activeAnimations = new HashSet<>();
	private final Button[] buttons = new Button[3];

	public static void main(final String[] args) throws FileNotFoundException {
		Application.launch(args);
	}

	@Override
	public void start(final Stage stage) throws Exception {
		stackPane = new StackPane();

		stackPane.setAlignment(Pos.TOP_LEFT);

		cardWidth = Bindings.min(stackPane.widthProperty().divide(8), stackPane.heightProperty().multiply(25D / (7 * 22)));
		xOffset = stackPane.widthProperty().subtract(cardWidth.multiply(8).subtract(10)).divide(2);

		for (int s = 0; s < 8; s++) {
			if (s != 3) {
				final Label topPlaceholder = makePlaceholder(cardWidth);
				topPlaceholder.translateXProperty().bind(xBinding(s, -1));
				stackPane.getChildren().add(topPlaceholder);
			}

			final Label mainPlaceholder = makePlaceholder(cardWidth);
			mainPlaceholder.translateXProperty().bind(xBinding(s, 0));
			mainPlaceholder.translateYProperty().bind(yBinding(s, 0));
			stackPane.getChildren().add(mainPlaceholder);
		}

		for (int c = 0; c < 3; c++) {
			final int color = c;

			final Button button = buttons[c] = new Button(Card.COLORS[c]);
			button.setOnAction(event -> {
				if (animating) return;

				final DragonCollectionResult res = game.canCollectDragons(color);
				if (res != null) {
					game.collectDragons(res);
					buttons[color].setDisable(true);

					animating = true;
					final ParallelTransition move = new ParallelTransition();

					for (final int slot : res.slots) {
						final int index = slot >= 8 ? -1 : game.cardsIn(slot);
						final Card card = cards.get(new Point(slot % 8, index));
						card.translateXProperty().unbind();
						card.translateYProperty().unbind();
						card.toFront();

						final TranslateTransition anim = new TranslateTransition(new Duration(TRANSLATE_DURATION), card);
						anim.toXProperty().bind(xBinding(res.destinationSlot, -1));
						anim.toYProperty().set(0);
						move.getChildren().add(anim);
					}

					move.setOnFinished(e -> {
						for (final int slot : res.slots) {
							final int index = slot >= 8 ? -1 : game.cardsIn(slot);
							final Card card = cards.get(new Point(slot % 8, index));

							stackPane.getChildren().remove(card);
							cards.removeValue(card);
						}

						final Card newCard = new Card(game.sideboardCard(res.destinationSlot), cardWidth, this::isDraggable);
						stackPane.getChildren().add(newCard);
						cards.put(new Point(res.destinationSlot, -1), newCard);

						newCard.translateXProperty().bind(xBinding(res.destinationSlot, -1));

						activeAnimations.remove(move);
						autocomplete(true);
					});
					activeAnimations.add(move);
					move.play();
				}
			});

			button.translateXProperty().bind(xBinding(3, -1));
			button.translateYProperty().bind(cardWidth.multiply(c * 7D / 15));
			button.minWidthProperty().bind(cardWidth.subtract(10));
			button.maxWidthProperty().bind(button.minWidthProperty());
			button.minHeightProperty().bind(cardWidth.multiply(7D / 15).subtract(5));
			button.maxHeightProperty().bind(button.minHeightProperty());

			button.setFont(new Font(button.heightProperty().getValue().doubleValue() / 8));
			button.heightProperty().addListener((observable, oldValue, newValue) -> {
				button.setFont(new Font(newValue.doubleValue() * 0.375));
			});
			button.setDisable(true);

			stackPane.getChildren().add(button);
		}

		final Button newGame = new Button("New Game");
		newGame.translateXProperty().bind(xBinding(6, -1));
		newGame.translateYProperty().bind(stackPane.heightProperty().subtract(newGame.heightProperty()).subtract(5));
		newGame.minWidthProperty().bind(cardWidth.multiply(2).subtract(10));
		newGame.maxWidthProperty().bind(newGame.minWidthProperty());
		newGame.minHeightProperty().bind(cardWidth.multiply(7D / 15).subtract(5));
		newGame.maxHeightProperty().bind(newGame.minHeightProperty());
		newGame.setOnAction(event -> {
			collectAndRestart();
		});
		newGame.setFont(new Font(newGame.heightProperty().getValue().doubleValue() / 8));
		newGame.heightProperty().addListener((observable, oldValue, newValue) -> {
			newGame.setFont(new Font(newValue.doubleValue() * 0.375));
		});
		stackPane.getChildren().add(newGame);

		stage.setScene(new Scene(stackPane, 640, 480));
		stage.show();

		startGame();
	}

	private void startGame() {
		game = new Game();
		stackPane.getChildren().removeIf(node -> node instanceof Card);
		cards.clear();
		for (final Button b : buttons)
			b.setDisable(true);

		final ParallelTransition deal = new ParallelTransition();
		for (int i = 0; i < 40; i++) {
			final int x = i % 8;
			final int y = i / 8;

			final Card card = new Card(game.cardAt(x, y), cardWidth, this::isDraggable);

			final ObservableValue<Number> startX = xBinding(4, -1, false), startY = yBinding(4, -1, false), endX = xBinding(x, y), endY = yBinding(x, y);

			final TranslateTransition anim = new TranslateTransition(Duration.millis(TRANSLATE_DURATION), card);
			anim.fromXProperty().bind(startX);
			anim.fromYProperty().bind(startY);
			anim.toXProperty().bind(endX);
			anim.toYProperty().bind(endY);
			anim.setDelay(Duration.millis(TRANSLATE_DURATION * i / 4D));
			anim.setOnFinished(event -> {
				card.translateXProperty().bind(endX);
				card.translateYProperty().bind(endY);
			});
			deal.getChildren().add(anim);

			card.setOnMove(createOnMove(card));

			card.setOnDrag(event -> {
				// Bring all cards being moved to the front, in order
				final Point position = cards.getKey(card);

				if (position.y < 0) {
					card.toFront();
					return;
				}

				final int n = game.cardsIn(position.x);
				for (int ix = position.y; ix < n; ix++) {
					cards.get(new Point(position.x, ix)).toFront();
				}
			});

			stackPane.getChildren().add(card);
			cards.put(new Point(x, y), card);
		}

		animating = true;
		deal.setOnFinished(e -> {
			autocomplete(true);
			activeAnimations.remove(deal);
		});
		activeAnimations.add(deal);
		deal.play();
	}

	private void collectAndRestart() {
		for (final Animation animation : activeAnimations) {
			animation.stop();
		}
		animating = true;
		final ParallelTransition collect = new ParallelTransition();
		for (final Card card : cards.values()) {
			final TranslateTransition move = new TranslateTransition(Duration.millis(TRANSLATE_DURATION), card);
			card.translateXProperty().unbind();
			card.translateYProperty().unbind();
			move.toXProperty().bind(xBinding(4, -1));
			move.toYProperty().bind(yBinding(4, -1));
			collect.getChildren().add(move);
		}

		collect.setOnFinished(event -> {
			startGame();
			animating = false;
			activeAnimations.remove(collect);
		});
		activeAnimations.add(collect);
		collect.play();
	}

	private ChangeListener<Point2D> createOnMove(final Card card) {
		return (observableNull, oldValue, newValue) -> {
			animating = true;

			final Point oldPosition = cards.getKey(card);

			final double x = (newValue.getX() - xOffset.doubleValue()) / cardWidth.doubleValue();

			// int newX = (int) ();

			boolean moved = false, destSideboard = false;
			int srcSlotCards = 0, destSlotCards = 0, destIndex = 0, newX = 0;
			for (int i = 0; i < 2 && !moved; i++) {
				newX = i == 0 ? (int) (x + 0.5) : x > newX ? (int) x + 1 : (int) x;

				if (newX < 0 || newX >= 8) {
					moved = false;
				} else {
					destSideboard = 2 * newValue.getY() < cardWidth.doubleValue() * (game.cardsIn(newX) + 5) * 7 / 25 + 16;

					// The number of cards originally in the source slot; there can only be one in
					// the sideboard
					srcSlotCards = oldPosition.y < 0 ? 1 : game.cardsIn(oldPosition.x);

					// The number of cards originally in the destination slot
					destSlotCards = destSideboard ? -1 : game.cardsIn(newX);

					// The destIndex parameter for game#move
					destIndex = destSideboard ? newX > 3 ? -2 : -1 : 0;
					try {
						moved = game.move(oldPosition.x, oldPosition.y, newX, destIndex);
					} catch (final Exception e) {
						moved = false;
					}
				}
			}

			final TranslateTransition anim = new TranslateTransition(Duration.millis(TRANSLATE_DURATION), card);
			ObservableValue<Number> xBinding, yBinding;

			Card oldCard = null;

			// Update the positions in #cards if cards were moved
			if (moved) {
				// Determine the color of the card if it's being completed
				if (destIndex == -2) newX = 5 + (card.card >> 4 & 0b11);

				// The number of cards that are being moved. There is always one card moved from
				// the sideboard.
				final int numCards = oldPosition.y >= 0 ? srcSlotCards - oldPosition.y : 1;

				if (numCards > 1) { // If more than one card is moved, move them sequentially
					for (int n = 0; n < numCards; n++) {
						cards.put(new Point(newX, destSlotCards + n), cards.get(new Point(oldPosition.x, oldPosition.y + n)));
					}
				} else {
					// Move the card to the new slot; if moving to the sideboard, use index
					// (-2, -1, or 0), otherwise use destSlotCards (0+)
					final Card old = cards.put(new Point(newX, destIndex < 0 ? destIndex : destSlotCards), card);
					if (destIndex == -2) oldCard = old;
				}

				xBinding = xBinding(newX, destSideboard ? -1 : destSlotCards);
				yBinding = yBinding(newX, destSideboard ? -1 : destSlotCards);
			} else {
				xBinding = xBinding(oldPosition.x, oldPosition.y);
				yBinding = yBinding(oldPosition.x, oldPosition.y);
			}

			anim.toXProperty().bind(xBinding);
			anim.toYProperty().bind(yBinding);
			card.translateXProperty().unbind();
			card.translateYProperty().unbind();

			final boolean finalMoved = moved;
			final Card finalOldCard = oldCard;
			anim.setOnFinished(event -> {
				card.translateXProperty().bind(xBinding);
				card.translateYProperty().bind(yBinding);
				if (finalMoved) {
					if (finalOldCard != null) {
						stackPane.getChildren().remove(finalOldCard);
					}

					autocomplete(true);
				} else {
					animating = false;
				}
				activeAnimations.remove(anim);
			});
			activeAnimations.add(anim);
			anim.play();
		};
	}

	private ObservableValue<Number> xBinding(final int slot, final int index) {
		return this.xBinding(slot, index, true);
	}

	private ObservableValue<Number> xBinding(final int slot, final int index, final boolean allowRelative) {
		if (!allowRelative || index < 1) return cardWidth.multiply(slot).add(xOffset);

		return cards.get(new Point(slot, index - 1)).translateXProperty();
	}

	private ObservableValue<Number> yBinding(final int slot, final int index) {
		return this.yBinding(slot, index, true);
	}

	private ObservableValue<Number> yBinding(final int slot, final int index, final boolean allowRelative) {
		if (index < 0) return new SimpleDoubleProperty(0);

		final int trueIndex = game == null ? 0 : Math.min(game.cardsIn(slot), index);
		if (!allowRelative || trueIndex == 0) return cardWidth.multiply((trueIndex + 5) * 7D / 25).add(16);

		return cards.get(new Point(slot, index - 1)).translateYProperty().add(cardWidth.multiply(7D / 25));
	}

	private void autocomplete(final boolean changeAnimating) {
		final int move = game.autoFill();
		if (move == -1) {
			if (game.isWon()) {
				winGame();
			} else {
				animating = false;
				for (int color = 0; color < 3; color++) {
					buttons[color].setDisable(game.canCollectDragons(color) == null);
				}
			}
		} else {
			Card card;
			if (move < 8) { // Main board
				card = cards.get(new Point(move, game.cardsIn(move) - 1));
			} else { // Sideboard
				card = cards.get(new Point(move % 8, -1));
			}
			card.toFront();
			game.move(move % 8, move >= 8 ? -1 : game.cardsIn(move) - 1, 0, -2);
			final int xTarget = card.card == Game.ROSE ? 4 : 5 + (card.card >> 4 & 0b11);
			final Card old = cards.put(new Point(xTarget, -2), card);

			final TranslateTransition tt = new TranslateTransition(Duration.millis(TRANSLATE_DURATION), card);
			tt.toXProperty().bind(xBinding(xTarget, 0));
			tt.setToY(0);
			card.translateXProperty().unbind();
			card.translateYProperty().unbind();

			tt.setOnFinished(event -> {
				card.translateXProperty().bind(xBinding(xTarget, 0));
				card.translateYProperty().set(0);

				if (old != null) stackPane.getChildren().remove(old);

				activeAnimations.remove(tt);
				autocomplete(changeAnimating);
			});
			activeAnimations.add(tt);
			tt.play();
		}
	}

	private void winGame() {
		final List<Card> cards = new ArrayList<>(this.cards.values());
		Collections.shuffle(cards);
		for (int i = 0; i < cards.size(); i++) {
			final FadeTransition fade = new FadeTransition(Duration.millis(TRANSLATE_DURATION), cards.get(i));
			fade.setFromValue(1);
			fade.setToValue(0);
			fade.setDelay(Duration.millis(0.5 * i * TRANSLATE_DURATION));
			if (i == cards.size() - 1) {
				fade.setOnFinished(event -> {
					activeAnimations.remove(fade);

					final PauseTransition pause = new PauseTransition(Duration.seconds(2));
					pause.setOnFinished(e -> {
						activeAnimations.remove(pause);
						startGame();
					});
					activeAnimations.add(pause);
					pause.play();
				});
			}
			activeAnimations.add(fade);
			fade.play();
		}
	}

	private boolean isDraggable(final Card card) {
		final Point position = cards.getKey(card);
		return !animating && game.canDrag(position.x, position.y);
	}

	private static Label makePlaceholder(final NumberBinding widthBinding) {
		final Label label = new Label();

		final NumberBinding wb = widthBinding.subtract(10);
		final NumberBinding hb = widthBinding.multiply(7D / 5).subtract(5);

		label.minWidthProperty().bind(wb);
		label.maxWidthProperty().bind(wb);
		label.minHeightProperty().bind(hb);
		label.maxHeightProperty().bind(hb);

		label.setBackground(Background.EMPTY);
		label.setBorder(new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, new CornerRadii(10), BorderWidths.DEFAULT)));

		return label;
	}

}
