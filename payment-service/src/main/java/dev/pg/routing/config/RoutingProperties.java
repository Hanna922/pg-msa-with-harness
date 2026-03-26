package dev.pg.routing.config;

import dev.pg.routing.model.AcquirerType;
import dev.pg.routing.model.CardBrand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pg.routing")
public class RoutingProperties {

    private String policy = "bin";
    private AcquirerType defaultAcquirer = AcquirerType.CARD_AUTHORIZATION_SERVICE;
    private Map<CardBrand, AcquirerType> brandAcquirerMap = new EnumMap<>(CardBrand.class);

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public AcquirerType getDefaultAcquirer() {
        return defaultAcquirer;
    }

    public void setDefaultAcquirer(AcquirerType defaultAcquirer) {
        this.defaultAcquirer = defaultAcquirer;
    }

    public Map<CardBrand, AcquirerType> getBrandAcquirerMap() {
        return brandAcquirerMap;
    }

    public void setBrandAcquirerMap(Map<CardBrand, AcquirerType> brandAcquirerMap) {
        this.brandAcquirerMap = brandAcquirerMap;
    }
}
