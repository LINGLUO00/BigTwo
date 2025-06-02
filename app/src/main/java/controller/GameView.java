package controller;

import models.Game;
import models.Player;
import models.Card;
import java.util.List;

/**
 * 游戏视图接口，定义控制器与视图之间的交互
 */
public interface GameView {

    // 更新游戏状态 
    void updateGameState(Game game);

    // 更新玩家手牌显示
    void updatePlayerHand(Player player);

    // 显示游戏消息
    void showMessage(String message);

    // 显示游戏结果
    void showGameResult(Player winner);

    // 更新上一手牌显示
    default void updateLastPlayedCards(List<Card> cards, Player player) {}

    // 显示调试信息
    default void showDebugInfo(String tag, String message) {
        showMessage("[" + tag + "] " + message);
    }
}
