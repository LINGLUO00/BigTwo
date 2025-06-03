package models;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级AI策略类，通过智能出牌决策提高AI玩家的胜率
 * 主要优化点：
 * 1. 首轮出牌主动寻找高价值五张牌型
 * 2. 下家只剩1张牌时的持续封锁策略
 * 3. 增强的牌型评估和风险判断机制
 */
public class AdvancedAIStrategy implements AIStrategy {
    private final PatternValidator validator = new PatternValidatorImpl();
    private boolean shouldBlock = false; // 跟踪是否需要封锁下家

    @Override
    public String getName() {
        return "Advanced AI";
    }

    /**
     * 决定AI的出牌策略
     * 
     * @param handSnapshot 当前手牌快照
     * @param lastPattern  上一手牌型
     * @param othersCount  其他玩家剩余手牌数量（按座位顺序排列）
     * @return 选择出的牌组
     */
    @Override
    public List<Card> makeMove(List<Card> handSnapshot, CardPattern lastPattern, List<Integer> othersCount) {
        if (handSnapshot == null || handSnapshot.isEmpty())
            return Collections.emptyList();

        handSnapshot.sort(null);

        // 更新封锁状态
        updateBlockStatus(othersCount);

        // 首轮出牌策略
        if (lastPattern == null) {
            return firstMove(handSnapshot);
        }

        // 跟牌策略
        return followMove(handSnapshot, lastPattern, othersCount);
    }

    /**
     * 更新封锁状态
     */
    private void updateBlockStatus(List<Integer> othersCount) {
        if (othersCount == null || othersCount.isEmpty()) {
            shouldBlock = false;
            return;
        }

        // 获取下家的手牌数量（假设下家是othersCount列表中的第一个元素）
        int nextPlayerCards = othersCount.get(0);
        shouldBlock = nextPlayerCards == 1;
    }

    /**
     * 首轮出牌策略 - 优化五张牌型的主动出牌
     */
    private List<Card> firstMove(List<Card> hand) {
        // 1. 尝试寻找高价值的五张牌型
        List<Card> fiveCardPattern = findBestFiveCardPattern(hand);
        if (!fiveCardPattern.isEmpty()) {
            return fiveCardPattern;
        }

        // 2. 常规逻辑：检查方块3及对子
        int idx = indexOfCard(hand, Card.DIAMOND, Card.THREE);
        if (idx >= 0) {
            Card d3 = hand.get(idx);
            List<Card> pair3 = cardsWithRank(hand, Card.THREE, 2);
            if (!pair3.isEmpty())
                return pair3;
            List<Card> straight = findStraightIncluding(hand, d3);
            if (!straight.isEmpty())
                return straight;
            List<Card> potentialSingle = findPotentialSingle(hand);
            if (!potentialSingle.isEmpty())
                return potentialSingle;
            return List.of(d3);
        }

        return List.of(hand.get(0));
    }

    private List<Card> findPotentialSingle(List<Card> hand) {
        // 统计每种点数的牌出现次数
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Card card : hand) {
            rankCount.put(card.getRank(), rankCount.getOrDefault(card.getRank(), 0) + 1);
        }

        // 优先选择有同点数其他牌的单牌
        for (Card card : hand) {
            if (rankCount.get(card.getRank()) > 1) {
                return List.of(card);
            }
        }

        return Collections.emptyList();
    }

    /**
     * 跟牌策略 - 增强封锁逻辑
     */
    private List<Card> followMove(List<Card> hand, CardPattern last, List<Integer> othersCount) {
        // 如果是AI主动出牌（lastPattern由AI自己打出），且处于封锁模式
        if (last != null && shouldBlock) {
            return makeBlockMove(hand, othersCount);
        }

        // 正常跟牌逻辑
        switch (last.getPatternType()) {
            case CardPattern.SINGLE:
                return beatSingle(hand, last.getHighestCard(), othersCount);
            case CardPattern.PAIR:
                return beatPair(hand, last, othersCount);
            case CardPattern.STRAIGHT:
                return beatStraight(hand, last, othersCount);
            case CardPattern.FLUSH:
                return beatFlush(hand, last, othersCount);
            case CardPattern.THREE_WITH_PAIR:
                return beatThreeWithPair(hand, last, othersCount);
            case CardPattern.BOMB:
                return beatBomb(hand, last, othersCount);
            case CardPattern.FLUSH_STRAIGHT:
                return beatFlushStraight(hand, last, othersCount);
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 封锁模式下的出牌策略
     */
    private List<Card> makeBlockMove(List<Card> hand, List<Integer> othersCount) {
        // 1. 优先出多张牌型
        List<Card> fiveCardPattern = findBestFiveCardPattern(hand);
        if (!fiveCardPattern.isEmpty()) {
            return fiveCardPattern;
        }

        // 2. 尝试出对子
        List<List<Card>> pairs = sortedPairs(hand);
        if (!pairs.isEmpty()) {
            return pairs.get(pairs.size() - 1); // 最大的对子
        }

        // 3. 尝试出三条（如果规则允许）
        List<List<Card>> threes = findThrees(hand);
        if (!threes.isEmpty()) {
            return threes.get(0); // 最大的三条
        }

        // 4. 只能出单张时，出最大的单张
        if (!hand.isEmpty()) {
            return List.of(hand.get(hand.size() - 1)); // 最大的单牌
        }

        return Collections.emptyList();
    }

    /**
     * 应对单牌的策略 - 增强封锁逻辑
     */
    private List<Card> beatSingle(List<Card> hand, Card target, List<Integer> othersCount) {
        CardPattern targetPattern = new CardPattern(CardPattern.SINGLE, List.of(target), target);
        List<Card> potentialBeats = new ArrayList<>();

        for (Card c : hand) {
            CardPattern cardPattern = new CardPattern(CardPattern.SINGLE, List.of(c), c);
            if (cardPattern.canBeat(targetPattern)) {
                potentialBeats.add(c);
            }
        }

        if (!potentialBeats.isEmpty()) {
            // 封锁模式：出最大的单牌
            if (shouldBlock) {
                return Collections.singletonList(potentialBeats.get(potentialBeats.size() - 1));
            }

            // 正常模式：选择最优单牌
            return Collections.singletonList(chooseBestSingle(potentialBeats, hand, othersCount));
        }

        return fallbackPowerPlays(hand, othersCount);
    }

    /**
     * 应对对子的策略 - 增强封锁逻辑
     */
    private List<Card> beatPair(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        List<List<Card>> pairs = sortedPairs(hand);
        pairs.sort(Comparator.comparing(l -> l.get(1)));

        // 封锁模式：优先选择较大的对子
        if (shouldBlock && !pairs.isEmpty()) {
            for (int i = pairs.size() - 1; i >= 0; i--) {
                List<Card> pair = pairs.get(i);
                CardPattern pairPattern = new CardPattern(CardPattern.PAIR, pair, pair.get(1));
                if (pairPattern.canBeat(tgt)) {
                    return pair;
                }
            }
        }

        // 正常模式：选择刚好能大过的最小对子
        for (List<Card> pair : pairs) {
            CardPattern pairPattern = new CardPattern(CardPattern.PAIR, pair, pair.get(1));
            if (pairPattern.canBeat(tgt)) {
                return pair;
            }
        }

        return fallbackPowerPlays(hand, othersCount);
    }

    /**
     * 应对顺子的策略 - 增强封锁逻辑
     */
    private List<Card> beatStraight(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        List<List<Card>> straights = sortedStraights(hand);
        straights.sort(Comparator.comparing(s -> s.get(4)));

        // 封锁模式：优先选择较大的顺子
        if (shouldBlock && !straights.isEmpty()) {
            for (int i = straights.size() - 1; i >= 0; i--) {
                List<Card> straight = straights.get(i);
                CardPattern straightPattern = new CardPattern(CardPattern.STRAIGHT, straight, straight.get(4));
                if (straightPattern.canBeat(tgt)) {
                    return straight;
                }
            }
        }

        // 正常模式：选择中间大小的顺子
        for (List<Card> straight : straights) {
            CardPattern straightPattern = new CardPattern(CardPattern.STRAIGHT, straight, straight.get(4));
            if (straightPattern.canBeat(tgt)) {
                return straight;
            }
        }

        return fallbackPowerPlays(hand, othersCount);
    }

    /**
     * 应对同花的策略
     */
    private List<Card> beatFlush(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        List<List<Card>> flushes = sortedFlushes(hand);
        flushes.sort(Comparator.comparing(s -> s.get(4)));

        for (List<Card> flush : flushes) {
            CardPattern flushPattern = new CardPattern(CardPattern.FLUSH, flush, flush.get(4));
            if (flushPattern.canBeat(tgt)) {
                return flush;
            }
        }

        return fallbackPowerPlays(hand, othersCount);
    }

    /**
     * 应对三带二的策略
     */
    private List<Card> beatThreeWithPair(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        for (List<Card> t : threeWithPairs(hand)) {
            CardPattern twpPattern = new CardPattern(CardPattern.THREE_WITH_PAIR, t, t.get(2));
            if (twpPattern.canBeat(tgt)) {
                return t;
            }
        }

        return fallbackPowerPlays(hand, othersCount);
    }

    /**
     * 应对炸弹的策略
     */
    private List<Card> beatBomb(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        List<List<Card>> bombs = sortedBombs(hand);

        for (List<Card> b : bombs) {
            CardPattern bombPattern = new CardPattern(CardPattern.BOMB, b, b.get(3));
            if (bombPattern.canBeat(tgt)) {
                // 封锁模式下更倾向于使用炸弹
                if (shouldBlock || shouldUseBomb(othersCount)) {
                    return b;
                }
            }
        }

        return findAnyFlushStraight(hand);
    }

    /**
     * 应对同花顺的策略
     */
    private List<Card> beatFlushStraight(List<Card> hand, CardPattern tgt, List<Integer> othersCount) {
        List<List<Card>> flushStraights = sortedFlushStraights(hand);

        for (List<Card> fs : flushStraights) {
            CardPattern fsPattern = new CardPattern(CardPattern.FLUSH_STRAIGHT, fs, fs.get(4));
            if (fsPattern.canBeat(tgt)) {
                return fs;
            }
        }

        return Collections.emptyList();
    }

    /**
     * 当无法跟牌时的备选策略
     */
    private List<Card> fallbackPowerPlays(List<Card> hand, List<Integer> othersCount) {
        // 尝试使用炸弹
        List<Card> bomb = findAnyBomb(hand);
        if (!bomb.isEmpty() && shouldUseBomb(othersCount)) {
            return bomb;
        }

        // 尝试使用同花顺
        List<Card> flushStraight = findAnyFlushStraight(hand);
        if (!flushStraight.isEmpty()) {
            return flushStraight;
        }

        return Collections.emptyList();
    }

    /**
     * 判断是否应该使用炸弹
     */
    private boolean shouldUseBomb(List<Integer> othersCount) {
        if (othersCount == null || othersCount.isEmpty())
            return false;

        // 当其他玩家手牌很少时，倾向于使用炸弹
        for (int count : othersCount) {
            if (count <= 2)
                return true;
        }

        return false;
    }

    /**
     * 寻找最优的五张牌型（按优先级：同花顺 > 炸弹 > 同花 > 顺子）
     */
    private List<Card> findBestFiveCardPattern(List<Card> hand) {
        // 1. 检查同花顺
        List<Card> flushStraight = findAnyFlushStraight(hand);
        if (!flushStraight.isEmpty()) {
            return flushStraight;
        }

        // 2. 检查炸弹（四张相同点数 + 任意一张）
        List<Card> bomb = findAnyBomb(hand);
        if (bomb.size() == 4) {
            List<Card> fullBomb = new ArrayList<>(bomb);
            // 找一张最小的单牌搭配炸弹
            for (Card c : hand) {
                if (!bomb.contains(c)) {
                    fullBomb.add(c);
                    return fullBomb;
                }
            }
        }

        // 3. 检查同花
        List<List<Card>> flushes = sortedFlushes(hand);
        if (!flushes.isEmpty()) {
            return flushes.get(0);
        }

        // 4. 检查顺子
        List<List<Card>> straights = sortedStraights(hand);
        if (!straights.isEmpty()) {
            return straights.get(0);
        }

        return Collections.emptyList();
    }

    // 以下是辅助方法，用于手牌分析和牌型识别

    private int indexOfCard(List<Card> hand, int suit, int rank) {
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.getSuit() == suit && c.getRank() == rank)
                return i;
        }
        return -1;
    }

    private List<Card> cardsWithRank(List<Card> hand, int rank, int count) {
        List<Card> out = new ArrayList<>();
        for (Card c : hand)
            if (c.getRank() == rank && out.size() < count)
                out.add(c);
        return out.size() == count ? out : Collections.emptyList();
    }

    private List<Card> findStraightIncluding(List<Card> hand, Card must) {
        for (int i = 0; i <= hand.size() - 5; i++) {
            List<Card> sub = hand.subList(i, i + 5);
            if (sub.contains(must) && validator.validate(sub).getPatternType() == CardPattern.STRAIGHT)
                return new ArrayList<>(sub);
        }
        return Collections.emptyList();
    }

    private List<List<Card>> sortedPairs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        for (int i = 0; i < hand.size() - 1; i++)
            for (int j = i + 1; j < hand.size(); j++)
                if (hand.get(i).getRank() == hand.get(j).getRank())
                    res.add(List.of(hand.get(i), hand.get(j)));
        res.sort(Comparator.comparing(l -> l.get(1)));
        return res;
    }

    private List<List<Card>> sortedStraights(List<Card> hand) {
        List<List<Card>> out = new ArrayList<>();
        for (int i = 0; i <= hand.size() - 5; i++) {
            List<Card> sub = hand.subList(i, i + 5);
            if (validator.validate(sub).getPatternType() == CardPattern.STRAIGHT)
                out.add(new ArrayList<>(sub));
        }
        out.sort(Comparator.comparing(l -> l.get(4)));
        return out;
    }

    private List<List<Card>> sortedFlushes(List<Card> hand) {
        Map<Integer, List<Card>> bySuit = new HashMap<>();
        for (Card c : hand)
            bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);
        List<List<Card>> res = new ArrayList<>();
        for (List<Card> suitList : bySuit.values())
            if (suitList.size() >= 5) {
                suitList.sort(null);
                res.add(suitList.subList(0, 5));
            }
        res.sort(Comparator.comparing(l -> l.get(4)));
        return res;
    }

    private List<List<Card>> threeWithPairs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        Map<Integer, List<Card>> byRank = new HashMap<>();

        // 按点数分组
        for (Card c : hand)
            byRank.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);

        // 找出所有三条和对子的组合
        for (Map.Entry<Integer, List<Card>> e : byRank.entrySet()) {
            if (e.getValue().size() >= 3) {
                List<Card> three = e.getValue().subList(0, 3);
                for (Map.Entry<Integer, List<Card>> pairEntry : byRank.entrySet()) {
                    if (pairEntry.getKey() != e.getKey() && pairEntry.getValue().size() >= 2) {
                        List<Card> pair = pairEntry.getValue().subList(0, 2);
                        List<Card> fullHouse = new ArrayList<>(three);
                        fullHouse.addAll(pair);
                        res.add(fullHouse);
                    }
                }
            }
        }

        return res;
    }

    private List<List<Card>> sortedBombs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        Map<Integer, List<Card>> byRank = new HashMap<>();

        // 按点数分组
        for (Card c : hand)
            byRank.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);

        // 找出所有四条
        for (List<Card> group : byRank.values()) {
            if (group.size() >= 4) {
                res.add(group.subList(0, 4));
            }
        }

        res.sort(Comparator.comparing(l -> l.get(3)));
        return res;
    }

    private List<List<Card>> sortedFlushStraights(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        Map<Integer, List<Card>> bySuit = new HashMap<>();

        // 按花色分组
        for (Card c : hand)
            bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);

        // 检查每个花色是否存在同花顺
        for (List<Card> suitList : bySuit.values()) {
            if (suitList.size() >= 5) {
                suitList.sort(null);
                for (int i = 0; i <= suitList.size() - 5; i++) {
                    List<Card> sub = suitList.subList(i, i + 5);
                    if (validator.validate(sub).getPatternType() == CardPattern.FLUSH_STRAIGHT) {
                        res.add(new ArrayList<>(sub));
                    }
                }
            }
        }

        res.sort(Comparator.comparing(l -> l.get(4)));
        return res;
    }

    private List<Card> findAnyBomb(List<Card> hand) {
        Map<Integer, List<Card>> byRank = new HashMap<>();
        for (Card c : hand)
            byRank.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
        for (List<Card> group : byRank.values()) {
            if (group.size() >= 4) {
                return group.subList(0, 4);
            }
        }
        return Collections.emptyList();
    }

    private List<Card> findAnyFlushStraight(List<Card> hand) {
        Map<Integer, List<Card>> bySuit = new HashMap<>();
        for (Card c : hand)
            bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);
        for (List<Card> suitList : bySuit.values()) {
            if (suitList.size() >= 5) {
                suitList.sort(null);
                for (int i = 0; i <= suitList.size() - 5; i++) {
                    List<Card> sub = suitList.subList(i, i + 5);
                    if (validator.validate(sub).getPatternType() == CardPattern.FLUSH_STRAIGHT) {
                        return sub;
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * 找出所有三条
     */
    private List<List<Card>> findThrees(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        Map<Integer, List<Card>> byRank = new HashMap<>();

        // 按点数分组
        for (Card c : hand)
            byRank.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);

        // 找出所有三条
        for (List<Card> group : byRank.values()) {
            if (group.size() >= 3) {
                res.add(group.subList(0, 3));
            }
        }

        res.sort(Comparator.comparing(l -> l.get(2)));
        return res;
    }

    /**
     * 选择最优的单牌 - 考虑牌型组合潜力和风险评估
     */
    private Card chooseBestSingle(List<Card> potentialBeats, List<Card> hand, List<Integer> othersCount) {
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Card card : hand) {
            rankCount.put(card.getRank(), rankCount.getOrDefault(card.getRank(), 0) + 1);
        }

        // 优先选择有对子或三条潜力的牌
        for (Card card : potentialBeats) {
            if (rankCount.get(card.getRank()) > 1) {
                return card;
            }
        }

        // 如果其他玩家手牌很少，尽量出大牌压制
        if (shouldPlayAggressively(othersCount)) {
            return potentialBeats.get(potentialBeats.size() - 1);
        }

        // 否则选择最小的可打牌，保留大牌优势
        return potentialBeats.get(0);
    }

    /**
     * 判断是否应该采取激进策略
     */
    private boolean shouldPlayAggressively(List<Integer> othersCount) {
        if (othersCount == null || othersCount.isEmpty())
            return false;

        for (int count : othersCount) {
            if (count <= 3)
                return true;
        }

        return false;
    }
}