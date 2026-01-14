package com.distributed.search.model;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * Service definition for Term Frequency (TF) calculation.
 * Workers implement this service to process documents assigned by the Leader.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: search.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TFServiceGrpc {

  private TFServiceGrpc() {}

  public static final String SERVICE_NAME = "com.distributed.search.model.TFService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.distributed.search.model.TFRequest,
      com.distributed.search.model.TFResponse> getCalculateTFMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CalculateTF",
      requestType = com.distributed.search.model.TFRequest.class,
      responseType = com.distributed.search.model.TFResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.distributed.search.model.TFRequest,
      com.distributed.search.model.TFResponse> getCalculateTFMethod() {
    io.grpc.MethodDescriptor<com.distributed.search.model.TFRequest, com.distributed.search.model.TFResponse> getCalculateTFMethod;
    if ((getCalculateTFMethod = TFServiceGrpc.getCalculateTFMethod) == null) {
      synchronized (TFServiceGrpc.class) {
        if ((getCalculateTFMethod = TFServiceGrpc.getCalculateTFMethod) == null) {
          TFServiceGrpc.getCalculateTFMethod = getCalculateTFMethod =
              io.grpc.MethodDescriptor.<com.distributed.search.model.TFRequest, com.distributed.search.model.TFResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CalculateTF"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distributed.search.model.TFRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distributed.search.model.TFResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TFServiceMethodDescriptorSupplier("CalculateTF"))
              .build();
        }
      }
    }
    return getCalculateTFMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TFServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TFServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TFServiceStub>() {
        @java.lang.Override
        public TFServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TFServiceStub(channel, callOptions);
        }
      };
    return TFServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TFServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TFServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TFServiceBlockingStub>() {
        @java.lang.Override
        public TFServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TFServiceBlockingStub(channel, callOptions);
        }
      };
    return TFServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TFServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TFServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TFServiceFutureStub>() {
        @java.lang.Override
        public TFServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TFServiceFutureStub(channel, callOptions);
        }
      };
    return TFServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * Service definition for Term Frequency (TF) calculation.
   * Workers implement this service to process documents assigned by the Leader.
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * RPC method to calculate TF for a list of documents based on a query.
     * </pre>
     */
    default void calculateTF(com.distributed.search.model.TFRequest request,
        io.grpc.stub.StreamObserver<com.distributed.search.model.TFResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCalculateTFMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service TFService.
   * <pre>
   **
   * Service definition for Term Frequency (TF) calculation.
   * Workers implement this service to process documents assigned by the Leader.
   * </pre>
   */
  public static abstract class TFServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return TFServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service TFService.
   * <pre>
   **
   * Service definition for Term Frequency (TF) calculation.
   * Workers implement this service to process documents assigned by the Leader.
   * </pre>
   */
  public static final class TFServiceStub
      extends io.grpc.stub.AbstractAsyncStub<TFServiceStub> {
    private TFServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TFServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TFServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * RPC method to calculate TF for a list of documents based on a query.
     * </pre>
     */
    public void calculateTF(com.distributed.search.model.TFRequest request,
        io.grpc.stub.StreamObserver<com.distributed.search.model.TFResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCalculateTFMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service TFService.
   * <pre>
   **
   * Service definition for Term Frequency (TF) calculation.
   * Workers implement this service to process documents assigned by the Leader.
   * </pre>
   */
  public static final class TFServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<TFServiceBlockingStub> {
    private TFServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TFServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TFServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * RPC method to calculate TF for a list of documents based on a query.
     * </pre>
     */
    public com.distributed.search.model.TFResponse calculateTF(com.distributed.search.model.TFRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCalculateTFMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service TFService.
   * <pre>
   **
   * Service definition for Term Frequency (TF) calculation.
   * Workers implement this service to process documents assigned by the Leader.
   * </pre>
   */
  public static final class TFServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<TFServiceFutureStub> {
    private TFServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TFServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TFServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * RPC method to calculate TF for a list of documents based on a query.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.distributed.search.model.TFResponse> calculateTF(
        com.distributed.search.model.TFRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCalculateTFMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CALCULATE_TF = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CALCULATE_TF:
          serviceImpl.calculateTF((com.distributed.search.model.TFRequest) request,
              (io.grpc.stub.StreamObserver<com.distributed.search.model.TFResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getCalculateTFMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.distributed.search.model.TFRequest,
              com.distributed.search.model.TFResponse>(
                service, METHODID_CALCULATE_TF)))
        .build();
  }

  private static abstract class TFServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TFServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.distributed.search.model.Search.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TFService");
    }
  }

  private static final class TFServiceFileDescriptorSupplier
      extends TFServiceBaseDescriptorSupplier {
    TFServiceFileDescriptorSupplier() {}
  }

  private static final class TFServiceMethodDescriptorSupplier
      extends TFServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    TFServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (TFServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TFServiceFileDescriptorSupplier())
              .addMethod(getCalculateTFMethod())
              .build();
        }
      }
    }
    return result;
  }
}
