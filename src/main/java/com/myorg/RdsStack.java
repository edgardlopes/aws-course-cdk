package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.Collections;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class RdsStack extends Stack {
    public RdsStack(final Construct scope, final String id, Vpc vpc) {
        this(scope, id, null, vpc);
    }

    public RdsStack(final Construct scope, final String id, final StackProps props, Vpc vpc) {
        super(scope, id, props);

        CfnParameter dbPassword = CfnParameter.Builder
                .create(this, "dbPassword")
                .type("String")
                .description("RDS password")
                .build();

        ISecurityGroup securityGroup = SecurityGroup.fromSecurityGroupId(this, id, vpc.getVpcDefaultSecurityGroup());
        securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(3306));


        DatabaseInstance db = DatabaseInstance.Builder
                .create(this, "Rds01")
                .instanceIdentifier("aws-project01-db")
                .engine(DatabaseInstanceEngine.mysql(MySqlInstanceEngineProps.builder().version(MysqlEngineVersion.VER_5_7).build()))
                .vpc(vpc)
                .credentials(Credentials.fromUsername("admin",
                        CredentialsFromUsernameOptions.builder()
                            .password(SecretValue.unsafePlainText(dbPassword.getValueAsString()))
                            .build()))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .multiAz(false)
                .allocatedStorage(10)
                .securityGroups(Collections.singletonList(securityGroup))
                .vpcSubnets(SubnetSelection.builder().subnets(vpc.getPrivateSubnets()).build())
                .build();

        CfnOutput.Builder.create(this, "rds-endpoint")
                .exportName("rds-endpoint")
                .value(db.getDbInstanceEndpointAddress())
                .build();

        CfnOutput.Builder.create(this, "rds-password")
                .exportName("rds-password")
                .value(dbPassword.getValueAsString())
                .build();
    }
}
