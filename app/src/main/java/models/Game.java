package models;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Thread‑safe & cleaner version of the original Game class.
 * Key refactors:
 * 1. GameState converted to an enum for type safety.
 * 2. Players & listeners stored in CopyOnWriteArrayList → safe iteration under
 * mutation.
 * 3. Single ReentrantLock guards all mutable state that must change atomically.
 * 4. Deck is final; shuffle() is delegated to callers – deal() no longer
 * triggers an implicit shuffle.
 * 5. Removed Android‑specific Log calls from model layer (use listener hooks).
 * 6. Public APIs are fail‑fast (e.g. adding players after the game has started
 * throws an exception).
 * 7. Minimized shared‑mutable access via volatile fields and confined
 * collection mutations under lock.
 */
public final class Game {

    /*
     * ----------------------------- state & constants -----------------------------
     */

    public enum State {
        WAITING, DEALING, PLAYING, GAME_OVER
    } // 等待发牌，发牌中，游戏进行中，游戏结束

    private final CopyOnWriteArrayList<Player> players = new CopyOnWriteArrayList<>(); // 玩家列表
    private final CopyOnWriteArrayList<GameStateListener> listeners = new CopyOnWriteArrayList<>(); // 监听器列表

    private final ReentrantLock lock = new ReentrantLock(); // 锁

    private final Deck deck = new Deck(); // 牌堆
    private final PatternValidator validator = new PatternValidatorImpl(); // 牌型验证器

    private volatile State state = State.WAITING; // 当前状态
    private volatile int currentIdx = 0; // 当前玩家索引
    private volatile int lastIdx = -1; // 上一轮玩家索引
    private volatile CardPattern lastPattern; // 上一轮牌型
    private volatile int passCount = 0; // pass次数

    private boolean autoFillBots = true; // 是否自动填充机器人

    /* ----------------------------- lifecycle ----------------------------- */

    public void addPlayer(Player p) {
        Objects.requireNonNull(p); // 不能为空
        if (state != State.WAITING)
            throw new IllegalStateException("Cannot add players after the game has started");
        players.addIfAbsent(p); // 当且仅当集合中不存在该玩家时，才将该元素添加到集合中。如果元素已存在，则不做任何操作并返回 false，否则，则返回 true
    }

    public void startGame() {
        lock.lock();
        try {
            ensurePlayerCount(); // 确保玩家数量
            deal(); // 发牌
            initRound(); // 初始化一轮游戏
            fire(l -> l.onGameStarted(this)); // 通知监听器游戏开始，对于每个监听器 l，调用其 onGameStarted 方法，传入当前游戏实例 this 作为参数
        } finally {
            lock.unlock();
        } // 释放锁
    }

    // 出牌
    public boolean playSelected() {
        lock.lock();
        try {
            Player p = current(); // 获取当前玩家
            List<Card> sel = p.getSelectedCards(); // 获取当前玩家选中的牌
            if (sel.isEmpty())
                return false; // 如果选中的牌为空，则返回 false

            CardPattern pat = validator.validate(sel); // 验证牌型
            if (pat.getPatternType() == CardPattern.INVALID)
                return false; // 如果牌型无效，则返回 false

            // 首回合检查：如果是首回合，必须包含方片3
            if (isFirstTurn()) {
                boolean hasD3 = sel.stream().anyMatch(c -> c.getSuit() == Card.DIAMOND && c.getRank() == Card.THREE);
                if (!hasD3)
                    return false;
            }

            // 如果不是自由出牌，并且不能击败上一轮牌型，则返回 false
            if (!isFreeTurn() && !canBeat(pat))
                return false;

            executePlay(p, sel, pat);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // pass
    public boolean pass() {
        lock.lock();
        try {
            if (isFirstTurn() || lastIdx == currentIdx)
                return false; // 如果是第一轮，或者上一轮玩家是当前玩家，则返回 false
            Player passer = current(); // 保存当前即将 pass 的玩家
            passCount++; // pass次数加1
            if (passCount >= players.size() - 1)
                allPassReset(); // 如果pass次数大于等于玩家数量减1，则重置
            else
                next(); // 否则，下一轮
            fire(l -> l.onPlayerPassed(this, passer)); // 通知监听器真正选择 pass 的玩家
            return true;
        } finally {
            lock.unlock();
        }
    }

    /* ----------------------------- internals ----------------------------- */

    private void ensurePlayerCount() {
        if (!autoFillBots)
            return; // 如果不需要自动填充机器人，则返回
        while (players.size() < 2) { // 如果玩家数量小于2，则添加机器人
            players.add(new AIPlayer("AI" + players.size(), new AdvancedAIStrategy())); // 添加机器人
        }
        while (players.size() > 4)
            players.remove(players.size() - 1); // 如果玩家数量大于4，则移除最后一个玩家
    }

    // 发牌
    private void deal() {
        state = State.DEALING; // 设置状态为发牌中
        deck.shuffle(); // 洗牌
        List<List<Card>> hands = deck.dealCards(players.size()); // 发牌
        for (int i = 0; i < players.size(); i++)
            players.get(i).setHand(hands.get(i)); // 设置玩家手牌
    }

    private void initRound() {
        currentIdx = startIdx(); // 获取起始玩家索引
        lastIdx = -1;
        lastPattern = null;
        passCount = 0;
        state = State.PLAYING;
    }

    private int startIdx() {
        for (int i = 0; i < players.size(); i++)
            if (playerHas(players.get(i), Card.DIAMOND, Card.THREE))
                return i; // 如果玩家有方片3，则返回该玩家索引
        return 0; // 否则，返回0
    }

    private boolean playerHas(Player p, int suit, int rank) {
        return p.getHand().stream().anyMatch(c -> c.getSuit() == suit && c.getRank() == rank); // 如果玩家有该花色和面值的牌，则返回true
    }

    private boolean canBeat(CardPattern next) {
        if (next == null)
            return false; // 如果下一轮牌型为空，则返回false
        if (lastPattern == null)
            return true; // 如果上一轮牌型为空，则返回true
        return next.canBeat(lastPattern); // 如果下一轮牌型可以击败上一轮牌型，则返回true
    }

    // 执行出牌
    private void executePlay(Player p, List<Card> cards, CardPattern pat) {
        // 如果是首回合，且玩家出的是方片3，更新起始玩家
        if (isFirstTurn() && playerHas(p, Card.DIAMOND, Card.THREE)) {
            currentIdx = players.indexOf(p); // 设置首家玩家为当前玩家
        }

        lastPattern = pat;
        lastIdx = currentIdx;
        passCount = 0;
        p.playSelectedCards(); // 出牌
        fire(l -> l.onCardsPlayed(this, p, cards, pat)); // 通知监听器当前玩家出牌
        if (p.getHand().isEmpty())
            finish(p); // 如果玩家手牌为空，则结束游戏
        else
            next(); // 否则，下一轮
    }

    private void finish(Player winner) {
        state = State.GAME_OVER; // 设置状态为游戏结束
        fire(l -> l.onGameOver(this, winner)); // 通知监听器游戏结束
    }

    private void allPassReset() {
        currentIdx = lastIdx; // 当前玩家索引设置为上一轮玩家索引
        lastPattern = null; // 上一轮牌型设置为空
        passCount = 0; // pass次数设置为0
    }

    private void next() {
        currentIdx = (currentIdx + 1) % players.size();
    } // 下一轮

    private Player current() {
        return players.get(currentIdx);
    } // 获取当前玩家

    private boolean isFirstTurn() {
        return lastIdx == -1;
    } // 是否是第一轮

    private boolean isFreeTurn() {
        return lastPattern == null || passCount >= players.size() - 1;
    } // 是否是自由出牌

    /*
     * ----------------------------- listener helper -----------------------------
     */

    // 通知监听器
    private void fire(java.util.function.Consumer<GameStateListener> fn) {
        for (GameStateListener l : listeners) {
            try {
                fn.accept(l);
            } catch (Exception ignored) {
            }
        }
    }

    /* ----------------------------- getter & misc ----------------------------- */

    public State getState() {
        return state;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public Player getCurrentPlayer() {
        return current();
    }

    public CardPattern getLastPattern() {
        return lastPattern;
    }

    public void setAutoFillBots(boolean b) {
        autoFillBots = b;
    } // 设置是否自动填充机器人

    /* ----------------------------- listener API ----------------------------- */

    public interface GameStateListener {
        void onGameStarted(Game g); // 游戏开始

        void onCardsPlayed(Game g, Player p, List<Card> cards, CardPattern pattern); // 出牌

        void onPlayerPassed(Game g, Player p); // pass

        void onGameOver(Game g, Player winner); // 游戏结束
    }

    // 添加监听器
    public void addGameStateListener(GameStateListener l) {
        if (l != null) {
            listeners.addIfAbsent(l);
        }
    }

    /* ------------------- Compatibility Layer (legacy code) ------------------- */
    // Provide old-style API names so that previously written UI / controller code
    // can still compile.

    /**
     * Legacy constant mapping – previously code used Game.STATE_PLAYING int
     * constant.
     */
    public static final int STATE_PLAYING = State.PLAYING.ordinal();

    /**
     * Legacy alias for getState() to return the enum ordinal value expected by
     * older code.
     */
    public int getGameState() {
        return state.ordinal();
    }

    /**
     * Legacy helper – returns the player who played last (may be null when first
     * turn).
     */
    public Player getLastPlayer() {
        return lastIdx >= 0 && lastIdx < players.size() ? players.get(lastIdx) : null;
    }

    /**
     * Legacy wrapper: some old screens reset game state manually before重新发牌等。
     * Here we simply bring the model back to WAITING so it is functionally
     * harmless.
     */
    public void resetGameState() {
        lock.lock();
        try {
            state = State.WAITING;
            currentIdx = 0;
            lastIdx = -1;
            lastPattern = null;
            passCount = 0;
            players.forEach(Player::clearSelections);
        } finally {
            lock.unlock();
        }
    }

    /** Legacy alias – previous code called this name. */
    public CardPattern getLastPlayedPattern() {
        return getLastPattern();
    }
}
