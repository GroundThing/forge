/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.gamemodes.quest;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import forge.gamemodes.quest.data.QuestPreferences;
import forge.gamemodes.quest.data.QuestPreferences.DifficultyPrefs;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.gamemodes.quest.io.QuestDuelReader;
import forge.model.FModel;
import forge.util.MyRandom;
import forge.util.maps.EnumMapOfLists;
import forge.util.maps.MapOfLists;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;

/**
 * QuestEventManager.
 *
 * @author Forge
 * @version $Id: QuestEventManager.java 20404 2013-03-17 05:34:13Z myk $
 */
public class QuestEventDuelManager implements QuestEventDuelManagerInterface {

    private final MapOfLists<QuestEventDifficulty, QuestEventDuel> sortedDuels = new EnumMapOfLists<>(QuestEventDifficulty.class, ArrayList::new);
    private final IStorage<QuestEventDuel> allDuels;

    /**
     * Instantiate all events and difficulty lists.
     *
     * @param dir &emsp; File object
     */
    public QuestEventDuelManager(final File dir) {
        allDuels = new StorageBase<>("Quest duels", new QuestDuelReader(dir));
        assembleDuelDifficultyLists();
    }

    public Iterable<QuestEventDuel> getAllDuels() {
        return allDuels;
    }

    public Iterable<QuestEventDuel> getDuels(QuestEventDifficulty difficulty) {
        return sortedDuels.get(difficulty);
    }

    // define fallback orders if there aren't enough opponents defined for a particular difficultly level
    private static List<QuestEventDifficulty> easyOrder = Arrays.asList(QuestEventDifficulty.EASY, QuestEventDifficulty.MEDIUM, QuestEventDifficulty.HARD, QuestEventDifficulty.EXPERT);
    private static List<QuestEventDifficulty> mediumOrder = Arrays.asList(QuestEventDifficulty.MEDIUM, QuestEventDifficulty.HARD, QuestEventDifficulty.EASY, QuestEventDifficulty.EXPERT);
    private static List<QuestEventDifficulty> hardOrder = Arrays.asList(QuestEventDifficulty.HARD, QuestEventDifficulty.MEDIUM, QuestEventDifficulty.EASY, QuestEventDifficulty.EXPERT);
    private static List<QuestEventDifficulty> expertOrder = Arrays.asList(QuestEventDifficulty.EXPERT, QuestEventDifficulty.HARD, QuestEventDifficulty.MEDIUM, QuestEventDifficulty.EASY);

    private List<QuestEventDifficulty> getOrderForDifficulty(QuestEventDifficulty d) {
        final List<QuestEventDifficulty> difficultyOrder;

        switch (d) {
            case EASY:
                difficultyOrder = easyOrder;
                break;
            case MEDIUM:
                difficultyOrder = mediumOrder;
                break;
            case HARD:
                difficultyOrder = hardOrder;
                break;
            case EXPERT:
                difficultyOrder = expertOrder;
                break;
            default:
                throw new RuntimeException("unhandled difficulty: " + d);
        }

        return difficultyOrder;
    }

    private void addDuel(List<QuestEventDuel> outList, QuestEventDifficulty targetDifficulty, int toAdd) {

        // if there's no way we can satisfy the request, return now
        if (allDuels.size() <= toAdd) {
            return;
        }

        final List<QuestEventDifficulty> difficultyOrder = getOrderForDifficulty(targetDifficulty);

        for (QuestEventDifficulty d : difficultyOrder) { // will add duels from preferred difficulty, will use others if the former has too few options.
            for (QuestEventDuel duel : sortedDuels.get(d)) {
                if (toAdd <= 0) {
                    return;
                }
                if (!outList.contains(duel)) {
                    outList.add(duel);
                    toAdd--;
                }
            }
        }

    }

    private void addRandomDuel(final List<QuestEventDuel> listOfDuels, final QuestEventDifficulty difficulty) {

        QuestEventDuel duel = new QuestEventDuel();

        List<QuestEventDifficulty> difficultyOrder = getOrderForDifficulty(difficulty);
        List<QuestEventDuel> possibleDuels = new ArrayList<>();
        for (QuestEventDifficulty diff : difficultyOrder) {
            possibleDuels = new ArrayList<>(sortedDuels.get(diff));
            if (!possibleDuels.isEmpty()) {
                break;
            }
        }

        QuestEventDuel randomOpponent = possibleDuels.get(((int) (possibleDuels.size() * MyRandom.getRandom().nextDouble())));

        duel.setTitle("Random Opponent");
        duel.setIconImageKey(randomOpponent.getIconImageKey());
        duel.setOpponentName(randomOpponent.getTitle());
        duel.setDifficulty(difficulty);
        duel.setProfile(randomOpponent.getProfile());
        duel.setShowDifficulty(false);
        duel.setDescription("Fight a random opponent");
        duel.setEventDeck(randomOpponent.getEventDeck());

        listOfDuels.add(duel);

    }

    /**
     * Generates an array of new duel opponents based on current win conditions.
     *
     * @return an array of {@link java.lang.String} objects.
     */
    public final List<QuestEventDuel> generateDuels() {

        final QuestPreferences questPreferences = FModel.getQuestPreferences();
        boolean moreDuelChoices = questPreferences.getPrefInt(QPref.MORE_DUEL_CHOICES) > 0;

        if (FModel.getQuest().getAchievements() == null) {
            return null;
        }

        final QuestController qCtrl = FModel.getQuest();
        final int numberOfWins = qCtrl.getAchievements().getWin();

        final int index = qCtrl.getAchievements().getDifficulty();
        final List<QuestEventDuel> duelOpponents = new ArrayList<>();

        QuestEventDifficulty randomDuelDifficulty = QuestEventDifficulty.EASY;

        if (numberOfWins < questPreferences.getPrefInt(DifficultyPrefs.WINS_MEDIUMAI, index)) {
            addDuel(duelOpponents, QuestEventDifficulty.EASY, 3);
            randomDuelDifficulty = QuestEventDifficulty.EASY;
        } else if (numberOfWins == questPreferences.getPrefInt(DifficultyPrefs.WINS_MEDIUMAI, index)) {
            addDuel(duelOpponents, QuestEventDifficulty.EASY, 1);
            addDuel(duelOpponents, QuestEventDifficulty.MEDIUM, 2);
            randomDuelDifficulty = QuestEventDifficulty.MEDIUM;
        } else if (numberOfWins < questPreferences.getPrefInt(DifficultyPrefs.WINS_HARDAI, index)) {
            addDuel(duelOpponents, QuestEventDifficulty.MEDIUM, 3);
            randomDuelDifficulty = QuestEventDifficulty.MEDIUM;
        } else if (numberOfWins == questPreferences.getPrefInt(DifficultyPrefs.WINS_HARDAI, index)) {
            addDuel(duelOpponents, QuestEventDifficulty.MEDIUM, 1);
            addDuel(duelOpponents, QuestEventDifficulty.HARD, 2);
            randomDuelDifficulty = QuestEventDifficulty.HARD;
        } else if (numberOfWins < questPreferences.getPrefInt(DifficultyPrefs.WINS_EXPERTAI, index)) {
            addDuel(duelOpponents, QuestEventDifficulty.HARD, 3);
            randomDuelDifficulty = QuestEventDifficulty.HARD;
        } else {
            addDuel(duelOpponents, QuestEventDifficulty.HARD, 2);
            addDuel(duelOpponents, QuestEventDifficulty.EXPERT, 1);
            if (MyRandom.getRandom().nextDouble() * 3 < 2) {
                randomDuelDifficulty = QuestEventDifficulty.HARD;
            } else {
                randomDuelDifficulty = QuestEventDifficulty.EXPERT;
            }
        }

        if (moreDuelChoices) {
            if (numberOfWins == questPreferences.getPrefInt(DifficultyPrefs.WINS_MEDIUMAI, index)) {
                addDuel(duelOpponents, QuestEventDifficulty.EASY, 1);
            } else if (numberOfWins < questPreferences.getPrefInt(DifficultyPrefs.WINS_HARDAI, index)) {
                addDuel(duelOpponents, QuestEventDifficulty.EASY, 1);
            } else if (numberOfWins == questPreferences.getPrefInt(DifficultyPrefs.WINS_HARDAI, index)) {
                addDuel(duelOpponents, QuestEventDifficulty.MEDIUM, 1);
            } else {
                addDuel(duelOpponents, QuestEventDifficulty.MEDIUM, 1);
                addDuel(duelOpponents, QuestEventDifficulty.EASY, 1);
            }
        }

        addRandomDuel(duelOpponents, randomDuelDifficulty);

        return duelOpponents;

    }

    /**
     * <p>
     * assembleDuelDifficultyLists.
     * </p>
     * Assemble duel deck difficulty lists
     */
    private void assembleDuelDifficultyLists() {

        sortedDuels.clear();
        sortedDuels.put(QuestEventDifficulty.EASY, new ArrayList<>());
        sortedDuels.put(QuestEventDifficulty.MEDIUM, new ArrayList<>());
        sortedDuels.put(QuestEventDifficulty.HARD, new ArrayList<>());
        sortedDuels.put(QuestEventDifficulty.EXPERT, new ArrayList<>());

        for (final QuestEventDuel qd : allDuels) {
            sortedDuels.add(qd.getDifficulty(), qd);
        }

    }

    /** */
    public void randomizeOpponents() {
        for (QuestEventDifficulty qd : sortedDuels.keySet()) {
            List<QuestEventDuel> list = (List<QuestEventDuel>) sortedDuels.get(qd);
            Collections.shuffle(list, MyRandom.getRandom());
        }
    }

}
