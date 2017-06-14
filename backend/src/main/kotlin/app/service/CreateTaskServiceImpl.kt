package app.service

import app.entity.Task
import app.repository.RepositoryException.Companion.handle
import app.repository.TaskRepository
import com.github.kittinunf.result.Validation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 *
 * @author nsoushi
 */

data class CreateTaskCommand(val title: String)

@Service("createTaskService")
class CreateTaskServiceImpl(private val taskRepository: TaskRepository) : ApplicationService<CreateTaskCommand, Task> {

    @Transactional
    override fun invoke(command: CreateTaskCommand): Task {
        return taskRepository.create(command.title).fold({
            task -> task
        }, {
            error -> throw handle(error)
        })
    }
}