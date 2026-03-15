package com.moid.addon.modules;

import com.moid.addon.MoidAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.ArrayList;
import java.util.List;

public class FakeScoreboard extends Module {
    private static final String SCOREBOARD_NAME = "moid_custom";
    private ScoreboardObjective customObjective;
    private ScoreboardObjective originalObjective;
    private final List<String> teamNames = new ArrayList<>();

    private long keyallStartTime = 0;
    private final long keyallInitialTime = 59 * 60 + 59;
    private long lastMsUpdate = 0;
    private int displayMs = 0;
    private int msChangeDirection = 1;
    private long lastScoreboardUpdate = 0;

    private final SettingGroup sgStats = settings.getDefaultGroup();

    // --- Settings with Figure.09 Reference & Purple/Pink Branding ---
    private final Setting<String> title = sgStats.add(new StringSetting.Builder().name("title").defaultValue("Moid-Addon").onChanged(s -> safeUpdate()).build());
    private final Setting<String> money = sgStats.add(new StringSetting.Builder().name("money").defaultValue("09").onChanged(s -> safeUpdate()).build());
    private final Setting<String> shards = sgStats.add(new StringSetting.Builder().name("shards").defaultValue("09").onChanged(s -> safeUpdate()).build());
    private final Setting<String> kills = sgStats.add(new StringSetting.Builder().name("kills").defaultValue("09").onChanged(s -> safeUpdate()).build());
    private final Setting<String> deaths = sgStats.add(new StringSetting.Builder().name("deaths").defaultValue("09").onChanged(s -> safeUpdate()).build());
    private final Setting<String> playtime = sgStats.add(new StringSetting.Builder().name("playtime").defaultValue("09h 09m").onChanged(s -> safeUpdate()).build());
    private final Setting<String> team = sgStats.add(new StringSetting.Builder().name("team").defaultValue("Figure.09").onChanged(s -> safeUpdate()).build());
    private final Setting<String> footer = sgStats.add(new StringSetting.Builder().name("footer").defaultValue("discord.com/Uq8TRfhHkc").onChanged(s -> safeUpdate()).build());

    public FakeScoreboard() {
        super(MoidAddon.CATEGORY, "fake-scoreboard", "Custom scoreboard overlay for Moid-Addon.");
    }

    private void safeUpdate() {
        if (isActive() && mc.world != null && mc.player != null) {
            updateScoreboard();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScoreboardUpdate >= 1000) {
            updateScoreboard();
            lastScoreboardUpdate = currentTime;
        }
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.player == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        originalObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        keyallStartTime = System.currentTimeMillis();
        lastMsUpdate = System.currentTimeMillis();
        displayMs = 9; // Starting at 9 for the theme
        msChangeDirection = 1;
        updateScoreboard();
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        cleanupTeams(scoreboard);

        if (customObjective != null) {
            scoreboard.removeObjective(customObjective);
            customObjective = null;
        }

        if (originalObjective != null) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, originalObjective);
        } else {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        }
        originalObjective = null;
    }

    private void cleanupTeams(Scoreboard scoreboard) {
        for (String teamName : teamNames) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) scoreboard.removeTeam(team);
        }
        teamNames.clear();
    }

    public void updateScoreboard() {
        if (mc.world == null || mc.player == null) return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        cleanupTeams(scoreboard);

        if (customObjective != null) {
            scoreboard.removeObjective(customObjective);
        }

        customObjective = scoreboard.addObjective(
                SCOREBOARD_NAME,
                ScoreboardCriterion.DUMMY,
                gradientTitle(title.get()),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                BlankNumberFormat.INSTANCE
        );
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, customObjective);

        List<MutableText> entries = generateEntriesText();

        for (int i = 0; i < entries.size(); i++) {
            String teamName = "moid_team_" + i;
            teamNames.add(teamName);

            Team t = scoreboard.addTeam(teamName);
            t.setPrefix(entries.get(i));

            String holderName = "§" + Integer.toHexString(i);
            ScoreHolder holder = ScoreHolder.fromName(holderName);

            ScoreAccess score = scoreboard.getOrCreateScore(holder, customObjective);
            score.setScore(entries.size() - i);

            scoreboard.addScoreHolderToTeam(holderName, t);
        }
    }

    private String getKeyallTimer() {
        long elapsed = System.currentTimeMillis() - keyallStartTime;
        long remainingSeconds = Math.max(0, keyallInitialTime - (elapsed / 1000));
        return String.format("%dm %ds", remainingSeconds / 60, remainingSeconds % 60);
    }

    private String getFooterWithMs() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMsUpdate > 2000) {
            displayMs += msChangeDirection * (1 + (int)(Math.random() * 3));
            if (displayMs < 5 || displayMs > 40) msChangeDirection *= -1;
            lastMsUpdate = currentTime;
        }

        String raw = footer.get();
        int start = raw.indexOf('(');
        return (start == -1) ? raw : raw.substring(0, start).trim() + "(" + displayMs + "ms)";
    }

    private List<MutableText> generateEntriesText() {
        return List.of(
                text(" "),
                colored("$ ", 0xA503FC).append(colored("Money: ", 0xFFFFFF)).append(colored(money.get(), 0xFC03BA)),
                colored("★ ", 0xA503FC).append(colored("Shards: ", 0xFFFFFF)).append(colored(shards.get(), 0xFC03BA)),
                colored("🗡 ", 0xA503FC).append(colored("Kills: ", 0xFFFFFF)).append(colored(kills.get(), 0xFC03BA)),
                colored("☠ ", 0xA503FC).append(colored("Deaths: ", 0xFFFFFF)).append(colored(deaths.get(), 0xFC03BA)),
                colored("⌛ ", 0xA503FC).append(colored("Keyall: ", 0xFFFFFF)).append(colored(getKeyallTimer(), 0xFC03BA)),
                colored("⌚ ", 0xA503FC).append(colored("Playtime: ", 0xFFFFFF)).append(colored(playtime.get(), 0xFC03BA)),
                colored("🪓 ", 0xA503FC).append(colored("Team: ", 0xFFFFFF)).append(colored(team.get(), 0xFC03BA)),
                text(" "),
                footerText()
        );
    }

    private MutableText footerText() {
        String raw = getFooterWithMs();
        int start = raw.indexOf('(');
        int end = raw.indexOf(')');
        if (start == -1 || end == -1) return colored(raw, 0xA0A0A0);

        return colored(raw.substring(0, start).trim() + " ", 0xA0A0A0)
                .append(colored("(", 0xA0A0A0))
                .append(colored(raw.substring(start + 1, end), 0xFC03BA))
                .append(colored(")", 0xA0A0A0));
    }

    private MutableText colored(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private MutableText text(String s) { return Text.literal(s); }

    private MutableText gradientTitle(String text) {
        // Purple (#A503FC) to Pink (#FC03BA)
        return gradient(text, 0xA503FC, 0xFC03BA);
    }

    private MutableText gradient(String text, int startColor, int endColor) {
        int startR = (startColor >> 16) & 0xFF, startG = (startColor >> 8) & 0xFF, startB = startColor & 0xFF;
        int endR = (endColor >> 16) & 0xFF, endG = (endColor >> 8) & 0xFF, endB = endColor & 0xFF;

        MutableText result = Text.empty();
        int len = Math.max(1, text.length());
        for (int i = 0; i < len; i++) {
            float t = (float) i / Math.max(len - 1, 1);
            int r = Math.round(startR + (endR - startR) * t);
            int g = Math.round(startG + (endG - startG) * t);
            int b = Math.round(startB + (endB - startB) * t);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb((r << 16) | (g << 8) | b))));
        }
        return result;
    }
}