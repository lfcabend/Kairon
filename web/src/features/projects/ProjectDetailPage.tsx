import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import { ProjectFormDialog } from "./ProjectFormDialog";
import { TaskBoard } from "./TaskBoard";
import { TaskFormDialog } from "./TaskFormDialog";
import { TaskTree } from "./TaskTree";
import { useProjectCategories } from "./useProjectCategories";
import { useProject, useDeleteProject } from "./useProjects";
import { useProjectTasks } from "./useProjectTasks";

export function ProjectDetailPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const { data: project, isLoading } = useProject(id);
  const { data: categories = [] } = useProjectCategories();
  const { tasks, topLevel, childrenByParentId } = useProjectTasks(id);
  const deleteProject = useDeleteProject();

  const [editingProject, setEditingProject] = useState(false);
  const [creatingTask, setCreatingTask] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isLoading) return null;
  if (!project) return <p className="text-sm text-muted-foreground">Project not found.</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: project.color }} aria-hidden />
            <h1 className="text-xl font-semibold">{project.name}</h1>
            <Badge variant="secondary">{project.status.replace("_", " ")}</Badge>
            {project.size && <Badge variant="outline">{project.size}</Badge>}
          </div>
          {project.description && <p className="mt-1 text-sm text-muted-foreground">{project.description}</p>}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setEditingProject(true)}>
            Edit
          </Button>
          <Button
            variant="outline"
            className="text-destructive"
            onClick={() => setConfirmDelete(true)}
          >
            Delete
          </Button>
        </div>
      </div>

      <Tabs defaultValue="tree">
        <div className="flex items-center justify-between">
          <TabsList>
            <TabsTrigger value="tree">List / Tree</TabsTrigger>
            <TabsTrigger value="board">Board</TabsTrigger>
          </TabsList>
          <Button size="sm" onClick={() => setCreatingTask(true)}>
            + New task
          </Button>
        </div>
        <TabsContent value="tree">
          <TaskTree projectId={id} topLevel={topLevel} childrenByParentId={childrenByParentId} />
        </TabsContent>
        <TabsContent value="board">
          {tasks.length === 0 ? (
            <p className="text-sm text-muted-foreground">No tasks yet.</p>
          ) : (
            <TaskBoard projectId={id} tasks={tasks} />
          )}
        </TabsContent>
      </Tabs>

      <ProjectFormDialog
        open={editingProject}
        onOpenChange={setEditingProject}
        project={project}
        categories={categories}
      />
      <TaskFormDialog
        open={creatingTask}
        onOpenChange={setCreatingTask}
        projectId={id}
        parentCandidates={topLevel}
      />

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete "{project.name}"?</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {tasks.length > 0
              ? `This project has ${tasks.length} task${tasks.length === 1 ? "" : "s"}. Delete all of them too?`
              : "This project has no tasks."}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setConfirmDelete(false)}>
              Cancel
            </Button>
            <Button
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={async () => {
                await deleteProject.mutateAsync(project.id);
                navigate("/projects");
              }}
            >
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
