package models;

import java.util.*;
import java.util.stream.Collectors;

public class AdvancedAIStrategy implements AIStrategy {
    private final PatternValidator validator = new PatternValidatorImpl();

    @Override
    public String getName() {
        return "Advanced AI";
    }

    @Override
    public List<Card> makeMove(List<Card> handSnapshot, CardPattern lastPattern, List<Integer> othersCount) {
        if (handSnapshot == null || handSnapshot.isEmpty()) return Collections.emptyList();
        handSnapshot.sort(null);
        return (lastPattern == null)
                ? firstMove(handSnapshot)
                : followMove(handSnapshot, lastPattern);
    }

    /* ------------------ first move ------------------ */
    private List<Card> firstMove(List<Card> hand) {
        int idx = indexOfCard(hand, Card.DIAMOND, Card.THREE);
        if (idx >= 0) {
            Card d3 = hand.get(idx);
            List<Card> pair3 = cardsWithRank(hand, Card.THREE, 2);
            if (!pair3.isEmpty()) return pair3;               // 对子 3‑3
            List<Card> straight = findStraightIncluding(hand, d3);
            if (!straight.isEmpty()) return straight;         // 顺子带 ♦3
            return List.of(d3);                               // 单出 ♦3
        }
        return List.of(hand.get(0));                          // 没有 ♦3: 出最小单张
    }

    /* ------------------ follow move dispatcher ------------------ */
    private List<Card> followMove(List<Card> hand, CardPattern last) {
        switch (last.getPatternType()) {
            case CardPattern.SINGLE:          return beatSingle(hand, last.getHighestCard());
            case CardPattern.PAIR:            return beatPair(hand, last);
            case CardPattern.STRAIGHT:        return beatStraight(hand, last);
            case CardPattern.FLUSH:           return beatFlush(hand, last);
            case CardPattern.THREE_WITH_PAIR: return beatThreeWithPair(hand, last);
            case CardPattern.BOMB:            return beatBomb(hand, last);
            case CardPattern.FLUSH_STRAIGHT:  return beatFlushStraight(hand, last);
            default:                          return Collections.emptyList();
        }
    }

    /* ------------------ beat helpers ------------------ */
    private List<Card> beatSingle(List<Card> hand, Card target) {
        // 创建 CardPattern 用于比较
        CardPattern targetPattern = new CardPattern(CardPattern.SINGLE, List.of(target), target);
        
        for (Card c : hand) {
            CardPattern cardPattern = new CardPattern(CardPattern.SINGLE, List.of(c), c);
            if (cardPattern.canBeat(targetPattern)) {
                return List.of(c);
            }
        }
        return fallbackPowerPlays(hand);
    }

    private List<Card> beatPair(List<Card> hand, CardPattern tgt) {
        List<List<Card>> pairs = sortedPairs(hand);
        pairs.sort(Comparator.comparing(l -> l.get(1))); // 比较对子中的最大牌
        
        for (List<Card> pair : pairs) {
            CardPattern pairPattern = new CardPattern(CardPattern.PAIR, pair, pair.get(1)); // 取对子中的最大牌作为比较基准
            if (pairPattern.canBeat(tgt)) {
                return pair;
            }
        }
        return fallbackPowerPlays(hand);
    }

    private List<Card> beatStraight(List<Card> hand, CardPattern tgt) {
        List<List<Card>> straights = sortedStraights(hand);
        straights.sort(Comparator.comparing(s -> s.get(4))); // 比较顺子中的最大牌
        
        for (List<Card> straight : straights) {
            CardPattern straightPattern = new CardPattern(CardPattern.STRAIGHT, straight, straight.get(4));
            if (straightPattern.canBeat(tgt)) {
                return straight;
            }
        }
        return fallbackPowerPlays(hand);
    }

    private List<Card> beatFlush(List<Card> hand, CardPattern tgt) {
        // 同样对每个可能的顺子、同花做比较，使用 CardPattern 来验证
        for (List<Card> flush : sortedFlushes(hand)) {
            CardPattern flushPattern = new CardPattern(CardPattern.FLUSH, flush, flush.get(4));
            if (flushPattern.canBeat(tgt)) {
                return flush;
            }
        }
        return fallbackPowerPlays(hand);
    }

    private List<Card> beatThreeWithPair(List<Card> hand, CardPattern tgt) {
        for (List<Card> t : threeWithPairs(hand)) {
            CardPattern twpPattern = new CardPattern(CardPattern.THREE_WITH_PAIR, t, t.get(2)); // 使用三张的最大牌作为比较
            if (twpPattern.canBeat(tgt)) {
                return t;
            }
        }
        return fallbackPowerPlays(hand);
    }

    private List<Card> beatBomb(List<Card> hand, CardPattern tgt) {
        for (List<Card> b : sortedBombs(hand)) {
            CardPattern bombPattern = new CardPattern(CardPattern.BOMB, b, b.get(3)); // 取炸弹的最大牌
            if (bombPattern.canBeat(tgt)) {
                return b;
            }
        }
        return findAnyFlushStraight(hand);                        // 同花顺可压炸弹
    }

    private List<Card> beatFlushStraight(List<Card> hand, CardPattern tgt) {
        for (List<Card> fs : sortedFlushStraights(hand)) {
            CardPattern fsPattern = new CardPattern(CardPattern.FLUSH_STRAIGHT, fs, fs.get(4)); // 使用最大牌作为比较
            if (fsPattern.canBeat(tgt)) {
                return fs;
            }
        }
        return Collections.emptyList();                           // 已是最大牌型
    }

    /* ------------------ fallback  ------------------ */
    private List<Card> fallbackPowerPlays(List<Card> hand) {
        List<Card> bomb = findAnyBomb(hand);
        if (!bomb.isEmpty()) return bomb;
        return findAnyFlushStraight(hand);
    }

    /* ================== combinator generators ================== */
    private int indexOfCard(List<Card> hand, int suit, int rank) {
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            if (c.getSuit() == suit && c.getRank() == rank) return i;
        }
        return -1;
    }

    private List<Card> cardsWithRank(List<Card> hand, int rank, int count) {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) if (c.getRank() == rank && out.size() < count) out.add(c);
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

    /* ------- enumerations (sorted small→large) ------- */
    private List<List<Card>> sortedPairs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        for (int i = 0; i < hand.size() - 1; i++)
            for (int j = i + 1; j < hand.size(); j++)
                if (hand.get(i).getRank() == hand.get(j).getRank())
                    res.add(List.of(hand.get(i), hand.get(j)));
        res.sort(Comparator.comparing(l -> l.get(1))); // compare by high card
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
        for (Card c : hand) bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);
        List<List<Card>> res = new ArrayList<>();
        for (List<Card> suitList : bySuit.values()) if (suitList.size() >= 5) {
            suitList.sort(null);
            for (int i = 0; i <= suitList.size() - 5; i++) {
                List<Card> sub = suitList.subList(i, i + 5);
                if (validator.validate(sub).getPatternType() == CardPattern.FLUSH)
                    res.add(new ArrayList<>(sub));
            }
        }
        res.sort(Comparator.comparing(l -> validator.validate(l).getHighestCard()));
        return res;
    }

    private List<List<Card>> threeWithPairs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        int[] cnt = new int[13];
        for (Card c : hand) cnt[c.getRank()]++;
        for (int r = 0; r < 13; r++) if (cnt[r] >= 3) {
            List<Card> triple = cardsWithRank(hand, r, 3);
            for (int pr = 0; pr < 13; pr++) if (pr != r && cnt[pr] >= 2) {
                List<Card> pair = cardsWithRank(hand, pr, 2);
                List<Card> twp = new ArrayList<>(triple);
                twp.addAll(pair);
                res.add(twp);
            }
        }
        res.sort(Comparator.comparing(l -> validator.validate(l).getHighestCard()));
        return res;
    }

    private List<List<Card>> sortedBombs(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        int[] cnt = new int[13];
        for (Card c : hand) cnt[c.getRank()]++;
        for (int r = 0; r < 13; r++) if (cnt[r] >= 4) res.add(cardsWithRank(hand, r, 4));
        res.sort(Comparator.comparing(l -> l.get(3))); // 4th card is high
        return res;
    }

    private List<List<Card>> sortedFlushStraights(List<Card> hand) {
        List<List<Card>> res = new ArrayList<>();
        for (List<Card> fs : flushStraights(hand)) res.add(fs);
        res.sort(Comparator.comparing(l -> validator.validate(l).getHighestCard()));
        return res;
    }

    /* ------------- individual powerful plays ------------- */
    private List<Card> findAnyBomb(List<Card> hand) {
        List<List<Card>> bombs = sortedBombs(hand);
        return bombs.isEmpty() ? Collections.emptyList() : bombs.get(0);
    }

    private List<Card> findAnyFlushStraight(List<Card> hand) {
        List<List<Card>> fs = sortedFlushStraights(hand);
        return fs.isEmpty() ? Collections.emptyList() : fs.get(0);
    }

    /* -------- helper: enumerate flush straights -------- */
    private List<List<Card>> flushStraights(List<Card> hand) {
        Map<Integer, List<Card>> bySuit = new HashMap<>();
        for (Card c : hand) bySuit.computeIfAbsent(c.getSuit(), k -> new ArrayList<>()).add(c);
        List<List<Card>> res = new ArrayList<>();
        for (List<Card> suitList : bySuit.values()) if (suitList.size() >= 5) {
            suitList.sort(null);
            for (int i = 0; i <= suitList.size() - 5; i++) {
                List<Card> sub = suitList.subList(i, i + 5);
                if (validator.validate(sub).getPatternType() == CardPattern.FLUSH_STRAIGHT)
                    res.add(new ArrayList<>(sub));
            }
        }
        return res;
    }
}
