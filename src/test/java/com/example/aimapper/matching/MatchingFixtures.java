package com.example.aimapper.matching;

import com.example.aimapper.matching.domain.FieldReference;
import com.example.aimapper.schema.domain.SchemaField;
import java.util.List;

/** 매칭 규칙 단위 테스트에서 반복 사용하는 필드 참조를 생성한다. */
public final class MatchingFixtures {
    private MatchingFixtures() {}
    public static FieldReference field(String path, String type, boolean nullable) {
        String name = path.substring(path.lastIndexOf('.') + 1).replace("[]", "");
        return new FieldReference("sample.Vo", new SchemaField(path, name, type, path.contains("[]"), nullable, null, List.of()));
    }
    public static FieldReference described(String description, String... annotations) {
        return new FieldReference("sample.Vo", new SchemaField("amount", "amount", "int", false, false, description, List.of(annotations)));
    }
}
