package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread‑safe Player implementation.
 */
public class Player {
    private final String name;  // 玩家名字
    private boolean isHuman;  // 是否是人类（允许后期标记）

    private final ReentrantLock lock = new ReentrantLock();  // 锁
    private final List<Card> hand = new CopyOnWriteArrayList<>();  // 手牌

    public Player(String name, boolean isHuman) {
        this.name = Objects.requireNonNull(name);  // 不能为空
        this.isHuman = isHuman;  // 是否是人类
    }

    /* ---------- basic info ---------- */
    public String getName() { return name; }  // 获取玩家名字
    public boolean isHuman() { return isHuman; }  // 是否是人类

    /**
     * 用于网络模式下收到发牌消息后，把本机玩家标记为人类。
     */
    public void setHuman(boolean human) { this.isHuman = human; }

    /* ---------- hand operations ---------- */
    public void setHand(List<Card> cards) {
        lock.lock();
        try {
            hand.clear();  // 清空手牌
            hand.addAll(cards);  // 添加手牌
            hand.sort(null);  // 排序
        } finally { lock.unlock(); }  // 释放锁
    }

    public List<Card> getHand() { return List.copyOf(hand); }

    public List<Card> getSelectedCards() {
        List<Card> sel = new ArrayList<>();
        for (Card c : hand) if (c.isSelected()) sel.add(c);
        return sel;
    }

    public void toggleCardSelection(int idx) {  // 切换选中的牌
        lock.lock();
        try {
            if (idx < 0 || idx >= hand.size()) return;  // 如果索引小于0或大于等于手牌数量，则返回
            Card c = hand.get(idx);  // 获取手牌
            c.setSelected(!c.isSelected());  // 切换选中状态
        } finally { lock.unlock(); }  // 释放锁
    }

    public void clearSelections() {  // 清空选中
        lock.lock();
        try {
            for (Card c : hand) c.setSelected(false);  // 设置所有手牌为未选中
        } finally { lock.unlock(); }  // 释放锁
    }

    public List<Card> playSelectedCards() {  // 出牌
        lock.lock();
        try {
            List<Card> out = new ArrayList<>();
            for (Card c : hand) if (c.isSelected()) out.add(c);
            if (!out.isEmpty()) {
                hand.removeAll(out);
                for (Card c : out) c.setSelected(false);
            }
            return out;
        } finally { lock.unlock(); }
    }

    public int getCardCount() { return hand.size(); }  // 获取手牌数量

    public boolean isCardSelected(int idx) {  // 是否选中
        return idx >= 0 && idx < hand.size() && hand.get(idx).isSelected();  // 如果索引在范围内且手牌被选中，则返回true
    }
}
