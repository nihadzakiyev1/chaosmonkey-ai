package dev.chaosmonkey.ai;
import dev.chaosmonkey.engine.*;
import static dev.chaosmonkey.engine.Model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ScenarioGeneratorTest {
    private final ScenarioGenerator generator=new ScenarioGenerator();
    @Test void reproducesPlanAndFaultSchedule(){assertThat(generator.generate(Category.NETWORK,42,15,2,Fault.LOST_RESPONSE,300)).isEqualTo(generator.generate(Category.NETWORK,42,15,2,Fault.LOST_RESPONSE,300));}
    @Test void validatorRejectsUnboundedActions(){var p=generator.generate(Category.RAPID,42,100,40,Fault.NONE,0);assertThatThrownBy(()->new Policy("http://localhost:8090").validate(p)).isInstanceOf(IllegalArgumentException.class);}
    @Test void remainsUsefulWithoutProvider(){var p=generator.generate(Category.CONCURRENCY,5,15,2,Fault.NONE,0);assertThat(generator.suggest(p,new Policy("http://localhost:8090"),"","","unused")).isSameAs(p);}
}
