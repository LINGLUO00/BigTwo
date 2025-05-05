package view;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.bigtwo.game.R;

/**
 * 游戏规则说明界面
 */
public class RulesActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);
        
        TextView tvRules = findViewById(R.id.tv_rules);
        tvRules.setMovementMethod(new ScrollingMovementMethod());
        
        // 设置规则文本
        String rulesText = "锄大地(Big Two)游戏规则：\n\n" + 
                "1. 基本规则：\n" +
                "   - 游戏使用一副52张扑克牌\n" +
                "   - 3-4名玩家参与\n" +
                "   - 目标是最先出完手中的牌\n\n" +
                "2. 牌的大小：\n" +
                "   - 2最大，A次之，K, Q, J, 10, 9, 8, 7, 6, 5, 4, 3最小\n" +
                "   - 同点数时，黑桃>红桃>梅花>方块\n\n" +
                "3. 牌型规则：\n" +
                "   - 单牌：任意一张牌\n" +
                "   - 对子：两张点数相同的牌\n" +
                "   - 三条：三张点数相同的牌\n" +
                "   - 三带一：三张点数相同的牌加一张单牌\n" +
                "   - 三带二：三张点数相同的牌加一对牌\n" +
                "   - 顺子：五张连续点数的牌(A可以作为K后面的牌)\n" +
                "   - 同花顺：同一花色的顺子\n" +
                "   - 炸弹：四张点数相同的牌加一张单牌\n\n" +
                "4. 游戏流程：\n" +
                "   - 游戏开始时，拥有方块3的玩家先出牌\n" +
                "   - 第一手牌必须包含方块3\n" +
                "   - 之后按照顺时针方向出牌\n" +
                "   - 出牌必须比上一手牌大或选择不出\n" +
                "   - 如果所有其他玩家都选择不出，则当前玩家可以自由出任何牌型\n" +
                "   - 最先出完所有牌的玩家获胜\n\n" +
                "5. 特殊规则：\n" +
                "   - 同花顺可以打任何牌型\n" +
                "   - 炸弹可以打除了同花顺以外的任何牌型\n" +
                "   - 可以出三条加一张单牌或三条加一个对子，用于组合牌型";
        
        tvRules.setText(rulesText);
    }
} 