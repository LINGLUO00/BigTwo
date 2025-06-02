package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的扑克牌牌堆（52 张，不含大小王）
 */
public final class Deck {

    /** 所有牌；只在写锁保护下修改 */
    private final List<Card> cards = new ArrayList<>(52);

    /** 读写分离锁：读操作可并发，写操作互斥 */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 创建新牌堆（自动填充 52 张牌）
     */
    public Deck() {
        initializeCards();
    }

    /** ---------- 公共 API ---------- */

    /** 使用随机种子洗牌 */
    public void shuffle(long seed) {
        lock.writeLock().lock();
        try {
            Collections.shuffle(cards, new Random(seed));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 使用 ThreadLocalRandom 洗牌 */
    public void shuffle() {
        shuffle(new Random().nextLong());
    }

    /**
     * 发牌；返回每位玩家的不可变手牌视图  
     * *若玩家数非法会抛 IllegalArgumentException*
     */
    public List<List<Card>> dealCards(int numPlayers) {
        if (numPlayers <= 0 || numPlayers > cards.size()) {
            throw new IllegalArgumentException("玩家数量必须在 1~52 之间");
        }

        lock.writeLock().lock();           // 发牌会读取并修改牌堆，因此加写锁
        try {
            int perPlayer = cards.size() / numPlayers;
            List<List<Card>> hands = new ArrayList<>(numPlayers);

            // 初始化手牌
            for (int i = 0; i < numPlayers; i++) {
                hands.add(new ArrayList<>(perPlayer));
            }

            // 轮流发牌
            int idx = 0;
            for (int round = 0; round < perPlayer; round++) {
                for (int i = 0; i < numPlayers; i++) {
                    hands.get(i).add(cards.get(idx++));
                }
            }

            // 返回只读视图，避免外部篡改
            hands.replaceAll(List::copyOf);
            return List.copyOf(hands);//返回所有玩家的牌

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前牌堆的只读快照
     */
    public List<Card> getCardsSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(cards);     // 深拷贝不可变视图
        } finally {
            lock.readLock().unlock();
        }
    }

    /** ---------- 私有辅助 ---------- */

    /** 初始化 52 张牌（需在写锁内调用） */
    private void initializeCards() {
        lock.writeLock().lock();
        try {
            cards.clear();
            for (int suit = Card.DIAMOND; suit <= Card.SPADE; suit++) {
                for (int rank = Card.THREE; rank <= Card.TWO; rank++) {
                    cards.add(new Card(suit, rank));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
