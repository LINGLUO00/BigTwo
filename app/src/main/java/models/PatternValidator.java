package models;

import java.util.List;

/**
 * 牌型验证器接口，用于判断一组牌是否构成特定牌型，提供给外部的接口，外部不关心具体的实现
 */
public interface PatternValidator {
    /**
     * 验证一组牌是否构成有效的牌型
     * @param cards 要验证的牌
     * @return 验证后的牌型，若无效则返回INVALID类型的牌型
     */
    CardPattern validate(List<Card> cards);
} 