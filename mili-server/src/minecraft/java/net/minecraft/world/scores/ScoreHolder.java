package net.minecraft.world.scores;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jspecify.annotations.Nullable;

public interface ScoreHolder {
    String WILDCARD_NAME = "*";
    ScoreHolder WILDCARD = new ScoreHolder() {
        @Override
        public String getScoreboardName() {
            return "*";
        }
    };

    String getScoreboardName();

    default @Nullable Component getDisplayName() {
        return null;
    }

    default Component getFeedbackDisplayName() {
        Component displayName = this.getDisplayName();
        return displayName != null
            ? displayName.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(this.getScoreboardName()))))
            : Component.literal(this.getScoreboardName());
    }

    static ScoreHolder forNameOnly(final String name) {
        if (name.equals("*")) {
            return WILDCARD;
        } else {
            final Component component = Component.literal(name);
            return new ScoreHolder() {
                @Override
                public String getScoreboardName() {
                    return name;
                }

                @Override
                public Component getFeedbackDisplayName() {
                    return component;
                }
            };
        }
    }

    static ScoreHolder fromGameProfile(GameProfile gameProfile) {
        final String string = gameProfile.name();
        return new ScoreHolder() {
            @Override
            public String getScoreboardName() {
                return string;
            }
        };
    }
}
