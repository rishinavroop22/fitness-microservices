package com.enterprise.knowledge.document.repository;

import com.enterprise.knowledge.document.model.DocumentStatus;
import com.enterprise.knowledge.document.model.KnowledgeDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    /**
     * Catalog search over title, file name and tags, limited to the departments the caller may see.
     * Pass an empty string for {@code q} to match everything.
     */
    @Query("""
            select distinct d from KnowledgeDocument d left join d.tags t
            where d.department in :departments
              and (:status is null or d.status = :status)
              and (lower(d.title) like lower(concat('%', :q, '%'))
                   or lower(d.fileName) like lower(concat('%', :q, '%'))
                   or lower(t) like lower(concat('%', :q, '%')))
            order by d.createdAt desc
            """)
    List<KnowledgeDocument> search(@Param("q") String q,
                                   @Param("departments") Collection<String> departments,
                                   @Param("status") DocumentStatus status,
                                   Pageable pageable);

    long countByStatus(DocumentStatus status);
}
