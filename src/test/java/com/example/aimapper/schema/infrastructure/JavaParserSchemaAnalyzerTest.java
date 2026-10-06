package com.example.aimapper.schema.infrastructure;

import com.example.aimapper.schema.application.JavaSourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** JavaParser 분석기가 클래스·필드·중첩 구조·주석·어노테이션을 정확히 추출하는지 검증한다. */
class JavaParserSchemaAnalyzerTest {

    private final JavaParserSchemaAnalyzer analyzer = new JavaParserSchemaAnalyzer();

    @Test
    void analyzesBasicCustomAndListTypesAsNestedPaths() {
        var sources = List.of(
                source("ProductRequest.java", """
                        package sample.commerce;
                        import jakarta.validation.constraints.NotNull;
                        import java.util.List;
                        public class ProductRequest {
                            /** Product price */
                            @NotNull private Money price;
                            private Boolean sellable;
                            private List<ProductOption> options;
                        }
                        """),
                source("Money.java", """
                        package sample.commerce;
                        import java.math.BigDecimal;
                        public class Money {
                            @jakarta.validation.constraints.NotNull
                            private BigDecimal amount;
                            private String currency;
                        }
                        """),
                source("ProductOption.java", """
                        package sample.commerce;
                        public class ProductOption {
                            private String optionName;
                            private int quantity;
                        }
                        """));

        var result = analyzer.analyze(sources);

        var product = result.stream().filter(it -> it.className().equals("ProductRequest")).findFirst().orElseThrow();
        assertThat(product.fields())
                .extracting("path")
                .containsExactly("price.amount", "price.currency", "sellable", "options[].optionName", "options[].quantity");
        assertThat(product.fields()).filteredOn(field -> field.path().equals("price.amount")).singleElement()
                .satisfies(field -> {
                    assertThat(field.javaType()).isEqualTo("java.math.BigDecimal");
                    assertThat(field.nullable()).isFalse();
                    assertThat(field.collection()).isFalse();
                });
        assertThat(product.fields()).filteredOn(field -> field.path().equals("options[].optionName")).singleElement()
                .satisfies(field -> assertThat(field.collection()).isTrue());
    }

    @Test
    void keepsAmbiguousLegacyFieldsWithoutInferringTheirMeaning() {
        var result = analyzer.analyze(List.of(source("LegacyProductVo.java", """
                package sample.legacy;
                public class LegacyProductVo {
                    private String value1;
                    private String option1;
                    private String saleAmount;
                }
                """)));

        assertThat(result).singleElement().satisfies(schemaClass ->
                assertThat(schemaClass.fields()).extracting("path")
                        .containsExactly("value1", "option1", "saleAmount"));
    }

    @Test
    void stopsAtCircularReference() {
        var result = analyzer.analyze(List.of(source("Node.java", """
                package sample;
                public class Node {
                    private String name;
                    private Node parent;
                }
                """)));

        assertThat(result.get(0).fields()).extracting("path").containsExactly("name", "parent");
    }

    private JavaSourceFile source(String name, String content) {
        return new JavaSourceFile(name, content);
    }
}
