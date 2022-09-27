import { Inject, Injectable, OnModuleInit } from '@nestjs/common';
import { ClientGrpcProxy } from '@nestjs/microservices';
import { GatewayConstants } from 'src/constants/gateway.constants';
import { vika } from 'src/grpc/generated/grpc/proto/socket.service';
import { grpc } from 'src/grpc/generated/grpc/proto/backend.serving.service';

@Injectable()
export class GrpcClient implements OnModuleInit {
  // backend-server grpc service
  private backendService: grpc.serving.ApiServingService;

  constructor(@Inject(GatewayConstants.BACKEND_SERVICE) private readonly backendClient: ClientGrpcProxy) {
  }

  async onModuleInit() {
    this.backendService = await this.backendClient.getService<grpc.serving.ApiServingService>('ApiServingService');
  }

  async recordNodeBrowsing(message: grpc.serving.NodeBrowsingRo): Promise<vika.grpc.BasicResult> {
    return await this.backendService.recordNodeBrowsing(message).toPromise();
  }
}