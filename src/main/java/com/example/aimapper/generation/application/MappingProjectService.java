package com.example.aimapper.generation.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.generation.domain.*;
import com.example.aimapper.schema.application.*;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 분석 추천과 사용자 결정을 분리해 관리하는 MVP용 인메모리 프로젝트 저장소이다.
 * 변경할 때 revision을 확인해 오래된 화면이 최신 결정을 덮어쓰지 못하게 한다.
 */
@Service
public class MappingProjectService {
    private final SchemaAnalysisService analysis;
    private final Map<UUID, StoredProject> projects = new ConcurrentHashMap<>();

    public MappingProjectService(SchemaAnalysisService analysis) { this.analysis = analysis; }

    /** 소스를 분석하고 모든 필드를 미매핑 상태로 초기화한 새 프로젝트를 만든다. */
    public MappingProject create(List<JavaSourceFile> asIs, List<JavaSourceFile> toBe) {
        var recommendations = analysis.analyze(asIs, toBe);
        UUID id = UUID.randomUUID();
        var decisions = recommendations.matches().stream().map(m -> new MappingDecision(
                new Target(m.source().className(), m.source().field().path()), MappingDecision.Status.UNMAPPED, null, null)).toList();
        var project = new MappingProject(id, 0, recommendations, decisions);
        projects.put(id, new StoredProject(project, List.copyOf(asIs), List.copyOf(toBe)));
        return project;
    }

    /** 프로젝트 ID로 현재 revision과 추천·결정을 조회한다. */
    public MappingProject get(UUID id) { return stored(id).project(); }

    /** 한 필드의 결정을 검증해 교체하고 revision을 원자적으로 증가시킨다. */
    public MappingProject decide(UUID id, long expectedRevision, MappingDecision decision) {
        if (decision == null || decision.source() == null || decision.status() == null) {
            throw new IllegalArgumentException("Source and decision status are required");
        }
        return projects.compute(id, (key, stored) -> {
            if (stored == null) throw new IllegalArgumentException("Unknown mapping project: " + id);
            var current = stored.project();
            if (current.revision() != expectedRevision) throw new IllegalArgumentException("Project revision changed; reload before updating");
            if (!exists(current.recommendations().asIs(), decision.source())) throw new IllegalArgumentException("Unknown source field");
            boolean selected = decision.status() == MappingDecision.Status.APPROVED || decision.status() == MappingDecision.Status.MODIFIED;
            if (selected && (decision.target() == null || decision.conversionType() == null
                    || !exists(current.recommendations().toBe(), decision.target()))) {
                throw new IllegalArgumentException("Selected mappings require a known target and conversion type");
            }
            if (!selected && (decision.target() != null || decision.conversionType() != null)) {
                throw new IllegalArgumentException("Rejected and unmapped decisions cannot have a target or conversion");
            }
            var updated = current.decisions().stream().map(d -> d.source().equals(decision.source()) ? decision : d).toList();
            return new StoredProject(new MappingProject(id, current.revision() + 1, current.recommendations(), updated), stored.asIs(), stored.toBe());
        }).project();
    }

    /** 코드 생성에 필요한 프로젝트와 원본 소스를 함께 조회한다. */
    public StoredProject stored(UUID id) {
        var stored = projects.get(id);
        if (stored == null) throw new IllegalArgumentException("Unknown mapping project: " + id);
        return stored;
    }

    private boolean exists(SchemaSnapshot snapshot, Target field) {
        return snapshot.classes().stream().filter(c -> c.qualifiedName().equals(field.className()))
                .flatMap(c -> c.fields().stream()).anyMatch(f -> f.path().equals(field.path()));
    }

    /** 인메모리에 보관되는 공개 프로젝트 상태와 양쪽 원본 소스 묶음이다. */
    public record StoredProject(MappingProject project, List<JavaSourceFile> asIs, List<JavaSourceFile> toBe) {}
}
