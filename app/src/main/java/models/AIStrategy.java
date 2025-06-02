package models;

import java.util.List;

/**
 * AI策略接口，定义AI玩家的决策行为
 */
public interface AIStrategy {
    /**
     * 根据当前游戏状态，做出出牌决策
     * 
     * @param aiHand AI玩家的手牌
     * @param lastPattern 上一个玩家出的牌型，若为null则表示可以自由出牌
     * @param otherPlayersCardCount 其他玩家的手牌数量
     * @return 选择要出的牌，若不出则返回空列表
     */
    List<Card> makeMove(List<Card> aiHand, CardPattern lastPattern, List<Integer> otherPlayersCardCount);
    
    /**
     * 获取策略名称
     */
    String getName();
} 