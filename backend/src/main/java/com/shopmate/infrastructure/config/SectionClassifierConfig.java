package com.shopmate.infrastructure.config;

import com.shopmate.domain.section.SectionClassifier;
import com.shopmate.domain.section.SectionDictionary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pure-domain {@link SectionClassifier} as a Spring bean.
 * {@code domain/section} must stay framework-free (ArchUnit), so the bean
 * definition lives here in infrastructure rather than on the domain classes
 * themselves.
 */
@Configuration
public class SectionClassifierConfig {

    @Bean
    public SectionDictionary sectionDictionary() {
        return new SectionDictionary();
    }

    @Bean
    public SectionClassifier sectionClassifier(SectionDictionary sectionDictionary) {
        return new SectionClassifier(sectionDictionary);
    }
}
