package com.kairon.projects.app;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.ProjectCategoryService.CreateCommand;
import com.kairon.projects.app.ProjectCategoryService.PatchCommand;
import com.kairon.projects.domain.ProjectCategory;
import com.kairon.projects.repo.ProjectCategoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCategoryServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000c1"));

    @Mock
    ProjectCategoryRepository categories;

    ProjectCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ProjectCategoryService(categories);
    }

    @Test
    void createAppendsAtMaxPositionPlus100() {
        when(categories.findByUserIdOrderByPositionAsc(USER.value()))
                .thenReturn(List.of(
                        ProjectCategory.create(USER.value(), "a", "#111111", 100),
                        ProjectCategory.create(USER.value(), "b", "#111111", 250)));
        when(categories.save(any(ProjectCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectCategoryView created = service.create(USER, new CreateCommand("New", null));

        assertThat(created.position()).isEqualTo(350);
        assertThat(created.name()).isEqualTo("New");
        assertThat(created.color()).isEqualTo("#6366f1");
    }

    @Test
    void createOnAnEmptySetStartsAt100() {
        when(categories.findByUserIdOrderByPositionAsc(USER.value())).thenReturn(List.of());
        when(categories.save(any(ProjectCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectCategoryView created = service.create(USER, new CreateCommand("First", "#abcdef"));

        assertThat(created.position()).isEqualTo(100);
        assertThat(created.color()).isEqualTo("#abcdef");
    }

    @Test
    void createRejectsADuplicateNameWith409() {
        when(categories.existsByUserIdAndName(USER.value(), "Home")).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.create(USER, new CreateCommand("Home", null)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
        verify(categories, never()).save(any());
    }

    @Test
    void reorderRewritesPositionsTo100200300InTheGivenOrder() {
        ProjectCategory a = ProjectCategory.create(USER.value(), "a", "#111111", 100);
        ProjectCategory b = ProjectCategory.create(USER.value(), "b", "#111111", 200);
        ProjectCategory c = ProjectCategory.create(USER.value(), "c", "#111111", 300);
        when(categories.findByUserIdOrderByPositionAsc(USER.value())).thenReturn(List.of(a, b, c));

        List<ProjectCategoryView> result = service.reorder(USER, List.of(c.getId(), a.getId(), b.getId()));

        assertThat(result).extracting(ProjectCategoryView::id)
                .containsExactly(c.getId(), a.getId(), b.getId());
        assertThat(result).extracting(ProjectCategoryView::position).containsExactly(100, 200, 300);
    }

    @Test
    void reorderRejectsAMismatchedIdSetWith400() {
        ProjectCategory a = ProjectCategory.create(USER.value(), "a", "#111111", 100);
        when(categories.findByUserIdOrderByPositionAsc(USER.value())).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reorder(USER, List.of(a.getId(), UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void aForeignOrMissingRowIs404() {
        UUID id = UUID.randomUUID();
        when(categories.findByIdAndUserId(id, USER.value())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.patch(USER, id, new PatchCommand("x", null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void staleExpectedVersionIs409() {
        ProjectCategory category = ProjectCategory.create(USER.value(), "a", "#111111", 100); // version 0
        when(categories.findByIdAndUserId(category.getId(), USER.value())).thenReturn(Optional.of(category));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.patch(USER, category.getId(),
                new PatchCommand(null, null, 7L)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void renamingRejectsACollisionWithAnotherCategory() {
        ProjectCategory category = ProjectCategory.create(USER.value(), "a", "#111111", 100);
        when(categories.findByIdAndUserId(category.getId(), USER.value())).thenReturn(Optional.of(category));
        when(categories.existsByUserIdAndNameAndIdNot(eq(USER.value()), eq("Work"), eq(category.getId())))
                .thenReturn(true);

        assertThatThrownBy(() -> service.patch(USER, category.getId(), new PatchCommand("Work", null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
    }

    @Test
    void deleteDoesNotTouchProjectRowsItself() {
        ProjectCategory category = ProjectCategory.create(USER.value(), "a", "#111111", 100);
        when(categories.findByIdAndUserId(category.getId(), USER.value())).thenReturn(Optional.of(category));

        service.delete(USER, category.getId());

        verify(categories).delete(category);
    }
}
