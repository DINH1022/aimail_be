package com.example.aimailbox.repository;

import com.example.aimailbox.model.KanbanColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KanbanColumnRepository extends JpaRepository<KanbanColumn, Long> {
    List<KanbanColumn> findByUserIdOrderByCreatedAtAsc(Long userId);
}
