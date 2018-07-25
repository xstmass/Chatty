package ru.mrbrikster.chatty.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

public interface ConfigurationNode {

    public Object get(Object def);

    boolean getAsBoolean(boolean def);

    String getAsString(String def);

    long getAsLong(long def);

    int getAsInt(int def);

    List getAsList(List def);

    List<Map<?, ?>> getAsMapList();

    List<String> getAsStringList();

    ConfigurationSection getAsConfigurationSection();

    ConfigurationNode getNode(String path);

    void set(Object value);

}
