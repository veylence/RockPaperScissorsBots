import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages a tournament between rock-paper-scissors players. Tournaments consist
 * of round-robin play wherein every player plays a match against every other
 * player. A match consists of a set number of games, with each game lasting a
 * set number of rounds.
 * <p>
 * Victors are determined as the player with the most games won. Ties are broken
 * by number of individual rounds won.
 */
public class TournamentManager {
    // Number of spaces between output columns
    private static final int COLUMN_SPACING = 3;
    // Percentage that tournament completion updates will be done in
    public static final double COMPLETION_PERCENT_UPDATE = 0.1;

    private List<PlayerData> players;
    private Map<Class<? extends RockPaperScissorsPlayer>, Object[]> trainingData;

    /**
     * Initializes a new tournament with no players.
     */
    public TournamentManager() {
        this.players = new ArrayList<>();
        this.trainingData = new HashMap<>();
    }

    /**
     * Adds a player to the tournament.
     *
     * @param newPlayer The player to add to the tournament.
     */
    public void add(Class<? extends RockPaperScissorsPlayer> newPlayer) {
        this.players.add(new PlayerData(newPlayer));
        this.trainingData.put(newPlayer, null);
    }

    /**
     * Runs a round-robin tournament between all loaded player. Each player
     * plays a match against every other player. A match consists of the given
     * number of games, with each game lasting the given number of rounds.
     *
     * @param rounds   The number of rounds to play per game.
     * @param games    The number of games to play per match between two
     *                 players.
     * @param training Whether the tournament should be run in training mode
     *                 where players have the ability to save and load data
     *                 between games.
     * @throws IllegalStateException If fewer than 2 players have been added to
     *                               the tournament.
     */
    public void runTournament(int rounds, int games, boolean training) {
        if (this.players.size() < 2) {
            throw new IllegalStateException("At least 2 players must be added to be able to run a tournament.");
        }

        resetPlayerData();

        System.out.println("Playing tournament with:");
        System.out.println("\t" + rounds + " round long games");
        System.out.println("\t" + games + " game long matches");
        System.out.println("\t" + this.players.size() + " competitors");
        System.out.println();

        // Total number of games that will be played
        int totalGames = 0;
        for (int i = 1; i <= this.players.size() - 1; i++) {
            totalGames += i;
        }
        totalGames *= games;
        // Tracks tournament completion percentage
        double gamePerPercent = 1.0 / totalGames;
        double percentDone = 0.0;
        double percentDoneShown = 0.0;
        System.out.print("Tournament Progress: 0%");

        // Have every player play a match against every other player
        for (int i = 0; i < this.players.size(); i++) {
            for (int j = i; j < this.players.size(); j++) {
                // Prevent a player from playing themselves
                if (i != j) {
                    // Play the given number of games
                    for (int game = 0; game < games; game++) {
                        playGame(this.players.get(i), this.players.get(j), rounds, training);

                        // Update tournament completion percentage
                        percentDone += gamePerPercent;
                        while (percentDoneShown < percentDone) {
                            percentDoneShown += COMPLETION_PERCENT_UPDATE;
                            if (percentDoneShown <= 1.0) {
                                System.out.printf(" %.0f%%", percentDoneShown * 100);
                            }
                        }
                    }
                }
            }
        }
        System.out.println();
        System.out.println();

        printRankings();
    }

    /**
     * Plays a single game consisting of the given number of rounds between the
     * two given players. Player data for both players is updated depending on
     * the results of the rounds and the game.
     *
     * @param player1Data The first player.
     * @param player2Data The second player.
     * @param rounds      The number of rounds the game should last.
     * @param training    Whether this should be a training game, where players
     *                    have the ability to save and load data that will be
     *                    persistent between games.
     */
    private void playGame(PlayerData player1Data, PlayerData player2Data, int rounds, boolean training) {
        RockPaperScissorsPlayer player1 = initializePlayer(player1Data);
        RockPaperScissorsPlayer player2 = initializePlayer(player2Data);

        // If a player could not be initialized, end the game
        if (handleDisqualifications(player1, player2, player1Data, player2Data)) {
            return;
        }

        // Load data if in training mode
        if (training) {
            Object[] p1TrainingData = player1.trainingInit(this.trainingData.get(player1.getClass()));
            this.trainingData.put(player1.getClass(), p1TrainingData);
            Object[] p2TrainingData = player2.trainingInit(this.trainingData.get(player2.getClass()));
            this.trainingData.put(player2.getClass(), p2TrainingData);
        }

        List<Move> player1Moves = new ArrayList<>();
        List<Move> player2Moves = new ArrayList<>();
        int player1RoundWins = 0;
        int player2RoundWins = 0;

        // Play the given number of rounds
        for (int round = 0; round < rounds; round++) {
            Move player1Move = getMove(player1, player2Moves);
            Move player2Move = getMove(player2, player1Moves);

            // If a player fails to return a valid move, end the game
            if (handleDisqualifications(player1Move, player2Move, player1Data, player2Data)) {
                return;
            }

            player1Moves.add(player1Move);
            player2Moves.add(player2Move);

            int result = player1Move.versus(player2Move);
            if (result == 0) {
                // Draw
                player1Data.getRoundsRecord().addDraw();
                player2Data.getRoundsRecord().addDraw();
            } else if (result == 1) {
                // Player 1 wins
                player1RoundWins++;
                player1Data.getRoundsRecord().addWin();
                player2Data.getRoundsRecord().addLoss();
            } else { // result == -1
                // Player 2 wins
                player2RoundWins++;
                player1Data.getRoundsRecord().addLoss();
                player2Data.getRoundsRecord().addWin();
            }
        }

        // Save data if in training mode
        if (training) {
            // Give a copy of the players' records so they can't edit them
            PlayerData.Record p1DataCopy = new PlayerData.Record(player1Data.getRoundsRecord());
            this.trainingData.put(player1.getClass(), player1.trainingEnd(p1DataCopy));
            PlayerData.Record p2DataCopy = new PlayerData.Record(player2Data.getRoundsRecord());
            this.trainingData.put(player2.getClass(), player2.trainingEnd(p2DataCopy));
        }

        // Update game records based on game results
        if (player1RoundWins > player2RoundWins) {
            player1Data.getGamesRecord().addWin();
            player2Data.getGamesRecord().addLoss();
            Rating.updateRatings(player1Data.getRating(), player2Data.getRating(), Rating.WIN_VALUE, Rating.LOSS_VALUE);
        } else if (player1RoundWins < player2RoundWins) {
            player1Data.getGamesRecord().addLoss();
            player2Data.getGamesRecord().addWin();
            Rating.updateRatings(player1Data.getRating(), player2Data.getRating(), Rating.LOSS_VALUE, Rating.WIN_VALUE);
        } else {
            player1Data.getGamesRecord().addDraw();
            player2Data.getGamesRecord().addDraw();
            Rating.updateRatings(player1Data.getRating(), player2Data.getRating(), Rating.DRAW_VALUE, Rating.DRAW_VALUE);
        }
        player1Data.updateNemesis(player2Data.getName(), player2RoundWins, rounds);
        player2Data.updateNemesis(player1Data.getName(), player1RoundWins, rounds);
    }

    /**
     * Prints player rankings in column form, displaying the number of games and
     * rounds won for each player as a fraction and percentage. Players are
     * ordered in descending order determined first by number of games won, then
     * by number of rounds won, then alphabetically by name. The nemesis of each
     * player is additionally shown, this being the player each player has lost
     * the most rounds to.
     */
    private void printRankings() {
        Collections.sort(this.players);

        // Get the stats of all players
        List<Map<String, String>> stats = new ArrayList<>();
        for (PlayerData playerData : this.players) {
            stats.add(playerData.getStats());
        }

        // Get the names of all stats and the width of each stat column
        List<String> columnNames = new ArrayList<>();
        List<Integer> columnNameLength = new ArrayList<>();
        for (String columnName : stats.get(0).keySet()) {
            columnNames.add(columnName);

            int longest = columnName.length();
            for (Map<String, String> playerStats : stats) {
                longest = Math.max(longest, playerStats.get(columnName).length());
            }
            columnNameLength.add(longest + COLUMN_SPACING);
        }

        // Get the total width of the stats table
        int totalLength = 0;
        for (int length : columnNameLength) {
            totalLength += length;
        }
        // Account for fencepost problem
        totalLength -= COLUMN_SPACING;

        // Print column names
        for (int i = 0; i < columnNames.size(); i++) {
            System.out.printf("%-" + columnNameLength.get(i) + "s", columnNames.get(i));
        }
        System.out.println();

        // Print column header separator bar
        for (int i = 0; i < totalLength; i++) {
            System.out.print("=");
        }
        System.out.println();

        // Print stats for each player
        for (Map<String, String> playerStats : stats) {
            for (int i = 0; i < columnNames.size(); i++) {
                System.out.printf("%-" + columnNameLength.get(i) + "s", playerStats.get(columnNames.get(i)));
            }
            System.out.println();
        }
    }

    /**
     * Attempts to initialize a new instance of a player. If the player cannot
     * be initialized, prints error message and stack trace.
     *
     * @param playerData The player to get a new instance of.
     * @return A new instance of the given player. Null if the player cannot be
     * initialized.
     */
    private RockPaperScissorsPlayer initializePlayer(PlayerData playerData) {
        RockPaperScissorsPlayer player = null;

        try {
            player = playerData.newInstance();
        } catch (Exception e) {
            System.err.println(playerData.getName() + " threw exception during initialization, game forfeited.");
            e.printStackTrace();
        }

        return player;
    }

    /**
     * Attempts to get the given player's next move. If the player fails to
     * return a valid move or throws an exception, prints error message and
     * stack trace if available.
     *
     * @param player The player to get a move from.
     * @return The next move made by the given player. Null if the player throws
     * an exception.
     */
    private Move getMove(RockPaperScissorsPlayer player, List<Move> previousMoves) {
        Move move = null;

        try {
            move = player.makeMove(previousMoves);
        } catch (Exception e) {
            System.err.println(player.getClass().getName() + " threw exception during move, game forfeited.");
            e.printStackTrace();
        }

        return move;
    }

    /**
     * Checks whether a player should be disqualified and updates the game
     * win/loss records of both players accordingly. If a player is
     * disqualified, they are given a game loss. If a player isn't disqualified,
     * but their opponent is, they are given a game win.
     *
     * @param player1     The player 1 value to check. If null, player 1 is
     *                    considered disqualified.
     * @param player2     The player 2 value to check. If null, player 2 is
     *                    considered disqualified.
     * @param player1Data The game data of player 1.
     * @param player2Data The game data of player 2.
     * @return True if a player has been disqualified, false otherwise.
     */
    private boolean handleDisqualifications(Object player1, Object player2, PlayerData player1Data, PlayerData player2Data) {
        if (player1 == null || player2 == null) {
            double player1Result;
            double player2Result;
            if (player1 == null) {
                player1Data.getGamesRecord().addLoss();
                player1Result = Rating.LOSS_VALUE;
            } else {
                player1Data.getGamesRecord().addWin();
                player1Result = Rating.WIN_VALUE;
            }
            if (player2 == null) {
                player2Data.getGamesRecord().addLoss();
                player2Result = Rating.LOSS_VALUE;
            } else {
                player2Data.getGamesRecord().addWin();
                player2Result = Rating.WIN_VALUE;
            }
            Rating.updateRatings(player1Data.getRating(), player2Data.getRating(), player1Result, player2Result);
            return true;
        }
        return false;
    }

    /**
     * Resets the win/loss/draw totals for all players.
     */
    public void resetPlayerData() {
        for (PlayerData player : this.players) {
            player.resetRecords();
        }
    }
}
