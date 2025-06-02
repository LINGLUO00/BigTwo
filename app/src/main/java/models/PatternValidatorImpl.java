package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 牌型验证器的标准实现，返回牌型
 */
public class PatternValidatorImpl implements PatternValidator {

    @Override
    public CardPattern validate(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return new CardPattern(CardPattern.INVALID, cards, null);
        }

        // 对牌进行排序，方便后续处理（假设 Card#compareTo 按点数升序排列）
        List<Card> sortedCards = new ArrayList<>(cards);
        Collections.sort(sortedCards);

        int size = sortedCards.size();

        switch (size) {
            case 1: // 单牌
                return new CardPattern(
                        CardPattern.SINGLE,
                        sortedCards,
                        findHighestCard(sortedCards) // 最高牌
                );
            case 2: // 对子
                if (isPair(sortedCards)) {
                    return new CardPattern(
                            CardPattern.PAIR,
                            sortedCards,
                            findHighestCard(sortedCards) // 对子的牌点
                    );
                }
                break;
            case 5: // 五张牌：顺子、同花、同花顺、三带二、炸弹
                if (isStraight(sortedCards)) {
                    return new CardPattern(
                            CardPattern.STRAIGHT,
                            sortedCards,
                            findHighestCard(sortedCards) // 顺子的最大牌
                    );
                } else if (isFlush(sortedCards)) {
                    return new CardPattern(
                            CardPattern.FLUSH,
                            sortedCards,
                            findHighestCard(sortedCards) // 同花的最大牌
                    );
                } else if (isFlushStraight(sortedCards)) {
                    return new CardPattern(
                            CardPattern.FLUSH_STRAIGHT,
                            sortedCards,
                            findHighestCard(sortedCards) // 同花顺的最大牌
                    );
                } else if (isThreeWithPair(sortedCards)) {
                    return new CardPattern(
                            CardPattern.THREE_WITH_PAIR,
                            sortedCards,
                            findCardWithTargetCount(sortedCards, 3) // 三张相同的牌点
                    );
                } else if (isBomb(sortedCards)) {
                    return new CardPattern(
                            CardPattern.BOMB,
                            sortedCards,
                            findCardWithTargetCount(sortedCards, 4) // 四张相同点数的牌
                    );
                }
                break;
            default:
                break;
        }

        return new CardPattern(CardPattern.INVALID, sortedCards, null);
    }

    // --------------------------- 辅助方法 ---------------------------

    // 计算牌中每个点数出现的次数（点数，次数）
    private Map<Integer, Integer> countRanks(List<Card> cards) {
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (Card card : cards) {
            rankCount.put(card.getRank(), rankCount.getOrDefault(card.getRank(), 0) + 1);
        }
        return rankCount;
    }

    /**
     * 返回牌序列中的最大牌,先比较点数，再比较花色（假设 sortedCards 已按点数升序）
     */
    private Card findHighestCard(List<Card> cards) {
        return cards.get(cards.size() - 1);
    }


    /**
     * 查找牌序列中某个点数出现次数等于 targetCount 的最大那张牌。
     */
    private Card findCardWithTargetCount(List<Card> cards, int targetCount) {
        Map<Integer, Integer> rankCount = countRanks(cards);
        //rankCount.get(card.getRank()) == targetCount - 表示某个点数出现了targetCount次
        //cards.stream() - 将牌列表转换为流，以便使用函数式操作
        //.filter(card -> rankCount.get(card.getRank()) == targetCount) - 过滤出符合条件的card
        //.max(Card::compareTo) - 取最大值
        //.orElse(null) - 如果找不到符合条件的card，返回null
        return cards.stream()
                .filter(card -> rankCount.get(card.getRank()) == targetCount)
                .max(Card::compareTo)
                .orElse(null);
    }

    private boolean isPair(List<Card> cards) {
        return cards.get(0).getRank() == cards.get(1).getRank();
    }

    private boolean isStraight(List<Card> cards) {
        for (int i = 0; i < cards.size() - 1; i++) {
            if (cards.get(i).getRank() + 1 != cards.get(i + 1).getRank()) {
                return false;
            }
        }
        //花色不是完全相同
        for (int i = 0; i < cards.size() - 1; i++) {
            if (cards.get(i).getSuit() != cards.get(i + 1).getSuit()) {
                return true;
            }
            if(i==3){
                return false;
            }
        }
        return true;
    }

    private boolean isFlush(List<Card> cards) {
        int suit = cards.get(0).getSuit();
        for (Card card : cards) {
            if (card.getSuit() != suit) return false;
        }
        return true;
    }

    private boolean isThreeWithPair(List<Card> cards) {
        Map<Integer, Integer> rankCount = countRanks(cards);
        //rankCount.size() == 2 - 表示只有两种不同的点数
        //rankCount.containsValue(3) - 表示有一种点数出现了3次
        //rankCount.containsValue(2) - 表示有一种点数出现了2次
        return rankCount.size() == 2 && rankCount.containsValue(3) && rankCount.containsValue(2);
    }

    private boolean isBomb(List<Card> cards) {
        Map<Integer, Integer> rankCount = countRanks(cards);
        return rankCount.size() == 2 && rankCount.containsValue(4);
    }

    private boolean isFlushStraight(List<Card> cards) {
        return isFlush(cards) && isStraight(cards);
    }


}
