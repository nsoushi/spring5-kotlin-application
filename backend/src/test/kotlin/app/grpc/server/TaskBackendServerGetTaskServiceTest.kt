package app.grpc.server

import app.WebAppException
import app.entity.Task
import app.grpc.handler.context.GRpcLogContextHandler
import app.grpc.handler.log.GRpcLogBuilder
import app.grpc.interceptor.ExceptionFilter
import app.grpc.server.gen.task.GetTaskInbound
import app.grpc.server.gen.task.TaskOutbound
import app.grpc.server.gen.task.TaskServiceGrpc
import app.service.DelegateTaskService
import app.service.GetTaskCommand
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.kotlintest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.time.LocalDateTime
import kotlin.reflect.KClass

@RunWith(PowerMockRunner::class)
@PrepareForTest(GRpcLogContextHandler::class)
class TaskBackendServerGetTaskServiceTest {

    private val UNIQUE_SERVER_NAME = "in-process server for " + TaskBackendServerGetTaskServiceTest::class
    lateinit var inProcessServer: Server
    lateinit var inProcessChannel: ManagedChannel

    lateinit var delegateTaskService: DelegateTaskService
    lateinit var target: TaskBackendServer

    private val metadataCaptor = ArgumentCaptor.forClass(io.grpc.Metadata::class.java)
    private val mockServerInterceptor = Mockito.spy(TestInterceptor())
    private class TestInterceptor : ServerInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(call: ServerCall<ReqT, RespT>?, headers: io.grpc.Metadata?,
                                                               next: ServerCallHandler<ReqT, RespT>?): ServerCall.Listener<ReqT> {
            return next!!.startCall(call, headers)
        }
    }

    private val exceptionInterceptor = ExceptionFilter()

    @Before
    fun setUp() {
        delegateTaskService = mock(DelegateTaskService::class)

        target = TaskBackendServer(delegateTaskService)
        inProcessServer = InProcessServerBuilder
                .forName(UNIQUE_SERVER_NAME)
                .addService(target)
                .intercept(exceptionInterceptor)
                .intercept(mockServerInterceptor)
                .directExecutor()
                .build()
        inProcessChannel = InProcessChannelBuilder.forName(UNIQUE_SERVER_NAME).directExecutor().build()

        inProcessServer.start()
    }

    @After
    fun tearDown() {
        inProcessChannel.shutdownNow()
        inProcessServer.shutdownNow()
    }

    @Test
    fun getProducts_onCompleted() {

        val taskId = 1L
        val request = GetTaskInbound.newBuilder().setTaskId(taskId.toInt()).build()

        val command = GetTaskCommand(taskId)
        val now = LocalDateTime.now()
        val task = Task(taskId.toInt(), "mocked Task", now, now, now)

        // mock
        mockStatic(GRpcLogContextHandler::class)
        Mockito.`when`(GRpcLogContextHandler.getLog()).thenReturn(GRpcLogBuilder())
        Mockito.`when`(delegateTaskService.getTask(command)).thenReturn(task)

        // request server
        val blockingStub = TaskServiceGrpc.newBlockingStub(inProcessChannel)
        val actual = blockingStub.getTaskService(request)

        // assertion
        actual.taskId shouldBe 1
        actual.title shouldBe "mocked Task"
    }

    @Test
    fun getProducts_NOT_FOUND() {

        val taskId = 1L
        val request = GetTaskInbound.newBuilder().setTaskId(taskId.toInt()).build()

        val command = GetTaskCommand(taskId)

        // mock
        mockStatic(GRpcLogContextHandler::class)
        Mockito.`when`(GRpcLogContextHandler.getLog()).thenReturn(GRpcLogBuilder())
        Mockito.`when`(delegateTaskService.getTask(command)).thenThrow(WebAppException.NotFoundException("not found"))

        try {
            // request server
            val blockingStub = TaskServiceGrpc.newBlockingStub(inProcessChannel)
            blockingStub.getTaskService(request)
        } catch (e: StatusRuntimeException) {
            // assertion
            e.status.code shouldBe Status.NOT_FOUND.code
            e.message shouldBe "NOT_FOUND: not found"

            Mockito.verify(mockServerInterceptor).interceptCall(
                    MockHelper.any<ServerCall<GetTaskInbound, TaskOutbound>>(),
                    metadataCaptor.capture(),
                    MockHelper.any<ServerCallHandler<GetTaskInbound, TaskOutbound>>())
            metadataCaptor.value.get(
                    Metadata.Key.of("custom_status", Metadata.ASCII_STRING_MARSHALLER)) shouldBe "404"
        }
    }

    @Test
    fun getProducts_INVALID_ARGUMENT() {

        val taskId = 0L
        val request = GetTaskInbound.newBuilder().setTaskId(taskId.toInt()).build()

        try {
            // request server
            val blockingStub = TaskServiceGrpc.newBlockingStub(inProcessChannel)
            blockingStub.getTaskService(request)
        } catch (e: StatusRuntimeException) {
            // assertion
            e.status.code shouldBe Status.INVALID_ARGUMENT.code
            e.message shouldBe "INVALID_ARGUMENT: invalid request"
        }
    }

    private class MockHelper {
        companion object {
            fun <T> any(): T {
                return Mockito.any()
                        ?: null as T
            }

            fun <T : Any> any(type: KClass<T>): T {
                return Mockito.any(type.java)
            }

            fun <T> eq(value: T): T {
                return if (value != null)
                    Mockito.eq(value)
                else
                    null
                            ?: null as T
            }
        }
    }
}
