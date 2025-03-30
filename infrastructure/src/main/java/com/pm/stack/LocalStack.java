package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.stream.Collectors;


public class LocalStack extends Stack {

    private final Vpc vpc;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope,id,props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB","auth-service-db");

        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB","patient-service-db");


        CfnHealthCheck authDbHealthCheck = createHealthCheck(authServiceDb,"AuthServiceDBHealthCheck");

        CfnHealthCheck patientDbHeathCheck = createHealthCheck(patientServiceDb,"PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

    }

    private Vpc createVpc(){
       return  Vpc.Builder.create(this,"PatientManagementVPC").vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder
                .create(this,id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnHealthCheck createHealthCheck(DatabaseInstance db, String id){
        return CfnHealthCheck.Builder.create(this,id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster(){
           return CfnCluster.Builder.create(this,"MskCluster")
                   .clusterName("kafka-cluster")
                   .kafkaVersion("2.8.0")
                   .numberOfBrokerNodes(1)
                   .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                           .instanceType("kafka.m5.xlarge")
                           .clientSubnets(vpc.getPrivateSubnets().stream()
                                   .map(ISubnet::getSubnetId)
                                   .collect(Collectors.toList()))
                           .brokerAzDistribution("DEFAULT").build())
                   .build();
    }


    public static void main(final String[] args){
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app,"localstack",props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
