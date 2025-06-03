package models;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.Collections;

public class AIPlayer extends Player {
    private volatile AIStrategy strategy;
    private static final Logger LOG = Logger.getLogger(AIPlayer.class.getName());

    public AIPlayer(String name, AIStrategy strategy) {
        super(name , false);  // 创建一个AI玩家
        this.strategy = Objects.requireNonNull(strategy);  // 不能为空
    }

    public AIStrategy getStrategy() { return strategy; }  // 获取策略
    public void setStrategy(AIStrategy strategy) { this.strategy = Objects.requireNonNull(strategy); }  // 设置策略

    /**
     * Core AI decision method – thread‑safe, no side‑effects outside Player's own lock.
     */
    public List<Card> makeDecision(CardPattern lastPattern, List<Integer> othersCount) {
        // snapshot hand for strategy (immutable)
        List<Card> handSnapshot = getHand();  // 获取手牌快照
        List<Card> decision = strategy.makeMove(new ArrayList<>(handSnapshot), lastPattern, othersCount);  // 做出决策

        if (decision == null || decision.isEmpty()) {
            clearSelections();
            return Collections.emptyList();
        }

        // apply selection using Player's locking APIs
        selectCards(decision);
        return getSelectedCards();
    }

    /* ---------- helpers ---------- */
    private void selectCards(List<Card> cardsToSelect) {
        clearSelections();  // 清空选中
        for (Card target : cardsToSelect) {
            int idx = indexOfCard(target);  // 获取目标卡牌的索引
            if (idx >= 0) toggleCardSelection(idx);  // 如果索引大于等于0，则切换选中状态
            else LOG.fine(() -> "AI could not find card: " + target.getDisplayName());  // 否则，记录日志
        }
    }

    private int indexOfCard(Card target) {
        List<Card> hand = getHand();  // 获取手牌
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.getRank() == target.getRank() && c.getSuit() == target.getSuit()) return i;  // 如果卡牌的rank和suit与目标卡牌相同，则返回索引
        }
        return -1;  // 否则，返回-1
    }

    /**
     * 执行AI自动出牌决策
     * @param game 当前游戏实例
     * @return 是否成功出牌
     */
    public boolean autoPlay(Game game) {
        if (game == null) return false;

        // 获取其他玩家手牌数量
        List<Integer> othersCount = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            if (p != this) {
                othersCount.add(p.getHand().size());
            }
        }

        // 做出决策
        makeDecision(game.getLastPattern(), othersCount);

        // 尝试出牌
        return game.playSelected();
    }
}
