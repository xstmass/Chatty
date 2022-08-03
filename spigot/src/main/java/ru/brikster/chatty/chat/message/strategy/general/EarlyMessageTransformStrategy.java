package ru.brikster.chatty.chat.message.strategy.general;

import org.jetbrains.annotations.NotNull;

public class EarlyMessageTransformStrategy extends GeneralMessageTransformStrategy<String> {

    @Override
    public @NotNull Stage getStage() {
        return Stage.EARLY;
    }

}