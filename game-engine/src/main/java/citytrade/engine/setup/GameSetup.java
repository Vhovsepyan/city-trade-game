package citytrade.engine.setup;

import citytrade.engine.CityType;
import citytrade.engine.GameRandom;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.MarketRules;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import citytrade.engine.ruleset.ProjectRules.ProjectCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StartingRules;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketPrices;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.PublicProject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Numbers Sheet 21: creates the starting state. Random draws happen in the fixed setup step order
 * (cities, objectives, events, projects, opportunities), so the same seed always gives the same game.
 */
public final class GameSetup {

    private static final int FIRST_OFFER_ID = 1;
    private static final int FIRST_CONTRACT_ID = 1;

    private GameSetup() {
    }

    public static GameState create(long seed, Ruleset ruleset) {
        checkRulesetFitsSetup(ruleset);
        GameRandom random = GameRandom.seeded(seed);

        // Step 1 (D11): seats 0-3 get the cities in seeded shuffle order.
        GameRandom.Shuffle<CityType> cities = random.shuffle(Arrays.asList(CityType.values()));
        random = cities.next();

        // Step 3 (D5): deal objective cards in seat order, dealtPerPlayer each.
        GameRandom.Shuffle<ObjectiveCard> objectives = random.shuffle(ruleset.objectives().deck());
        random = objectives.next();
        int dealt = ruleset.objectives().dealtPerPlayer();

        // Step 2: starting resources at the lowest city level.
        int startLevel = startingLevel(ruleset);
        List<PlayerState> players = new ArrayList<>();
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            CityType city = cities.items().get(seat);
            List<ObjectiveCard> hand = objectives.items().subList(seat * dealt, (seat + 1) * dealt);
            players.add(PlayerState.starting(seat, city, startingHoldings(city, ruleset.starting()), startLevel, hand));
        }

        // Step 4: the top event card is revealed as the warning for the first event round.
        GameRandom.Shuffle<EventCard> events = random.shuffle(ruleset.events().deck());
        random = events.next();
        List<EventCard> eventDeck = events.items();
        Optional<EventWarning> warning = Optional.empty();
        if (!eventDeck.isEmpty()) {
            warning = Optional.of(new EventWarning(ruleset.events().eventRounds().getFirst(), eventDeck.getFirst()));
            eventDeck = eventDeck.subList(1, eventDeck.size());
        }

        // Step 5: draw one project card per window; the rest are put away unseen.
        GameRandom.Shuffle<ProjectCard> projectCards = random.shuffle(ruleset.projects().cards());
        random = projectCards.next();
        List<PublicProject> projects = new ArrayList<>();
        for (int i = 0; i < ruleset.projects().windows().size(); i++) {
            projects.add(PublicProject.upcoming(projectCards.items().get(i), ruleset.projects().windows().get(i)));
        }

        // Step 6: face-down opportunity pile.
        GameRandom.Shuffle<OpportunityCard> opportunities = random.shuffle(ruleset.opportunities().cards());
        random = opportunities.next();

        // Step 7: every resource starts at the ruleset's start step.
        MarketPrices market = MarketPrices.allAt(startStepIndex(ruleset.market()));

        return new GameState(ruleset.version(), 0, GamePhase.SETUP, random, players, market, eventDeck, warning,
                Optional.empty(), projects, opportunities.items(), List.of(), List.of(), FIRST_OFFER_ID, List.of(), FIRST_CONTRACT_ID);
    }

    private static ResourceBundle startingHoldings(CityType city, StartingRules starting) {
        return new ResourceBundle(
                startingAmount(Resource.FOOD, city, starting),
                startingAmount(Resource.ENERGY, city, starting),
                startingAmount(Resource.MATERIALS, city, starting),
                startingAmount(Resource.TECHNOLOGY, city, starting),
                starting.money());
    }

    private static int startingAmount(Resource resource, CityType city, StartingRules starting) {
        return resource == city.specialty() ? starting.specialtyResource() : starting.otherResource();
    }

    private static int startingLevel(Ruleset ruleset) {
        return ruleset.levels().stream()
                .map(LevelRules::level)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("ruleset has no levels"));
    }

    private static int startStepIndex(MarketRules market) {
        for (int i = 0; i < market.steps().size(); i++) {
            if (market.steps().get(i).name().equals(market.startStep())) {
                return i;
            }
        }
        throw new IllegalArgumentException("market start step '" + market.startStep() + "' is not a market step");
    }

    /** Setup needs these shapes; a ruleset loaded through the validator always has them. */
    private static void checkRulesetFitsSetup(Ruleset ruleset) {
        if (ruleset.playerCount() != CityType.values().length) {
            throw new IllegalArgumentException("player count " + ruleset.playerCount()
                    + " must equal the number of cities " + CityType.values().length);
        }
        int neededObjectives = ruleset.objectives().dealtPerPlayer() * ruleset.playerCount();
        if (ruleset.objectives().deck().size() < neededObjectives) {
            throw new IllegalArgumentException("objective deck has " + ruleset.objectives().deck().size()
                    + " cards, setup deals " + neededObjectives);
        }
        if (ruleset.events().deck().size() != ruleset.events().eventRounds().size()) {
            throw new IllegalArgumentException("event deck size " + ruleset.events().deck().size()
                    + " must equal the number of event rounds " + ruleset.events().eventRounds().size());
        }
        if (ruleset.projects().cards().size() < ruleset.projects().windows().size()) {
            throw new IllegalArgumentException("project deck has " + ruleset.projects().cards().size()
                    + " cards for " + ruleset.projects().windows().size() + " windows");
        }
        if (ruleset.opportunities().cards().size() < ruleset.opportunities().appearanceRounds().size()) {
            throw new IllegalArgumentException("opportunity deck has " + ruleset.opportunities().cards().size()
                    + " cards for " + ruleset.opportunities().appearanceRounds().size() + " appearance rounds");
        }
    }
}
