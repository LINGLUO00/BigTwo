package controller;

import models.Game;
import models.Player;

/**
 * 游戏视图接口，定义控制器与视图之间的交互
 */
public interface GameView {
    /**
     * 更新游戏状态
     * @param game 游戏模型
     */
    void updateGameState(Game game);
    
    /**
     * 更新玩家手牌显示
     * @param player 玩家
     */
    void updatePlayerHand(Player player);
    
    /**
     * 显示游戏消息
     * @param message 消息内容
     */
    void showMessage(String message);
    
    /**
     * 显示游戏结果
     * @param winner 获胜玩家
     */
    void showGameResult(Player winner);
    
    /**
     * 显示调试信息
     * @param tag 调试标签
     * @param message 调试信息
     */
    default void showDebugInfo(String tag, String message) {
        // 默认实现，简单显示为普通消息
        showMessage("[" + tag + "] " + message);
    }
} 