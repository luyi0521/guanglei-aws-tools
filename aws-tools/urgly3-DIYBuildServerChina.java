import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sun.misc.BASE64Encoder;
import sun.awt.image.URLImageSource;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateImageRequest;
import com.amazonaws.services.ec2.model.DescribeImageAttributeRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

/*
 * guanglei@
 * Don't try it in PRODUCTION, this is a simple demo to use Java SDK to create a micro auto build server.
 * Sample event payload:
 * To make this demo work, a record in dynamodb must be provisioned. Will improve this.
 * "Subject" : "Amazon S3 Notification",
 * "Message" : "{\"Records\":[{\"eventVersion\":\"2.0\",\"eventSource\":\"aws:s3\",\"awsRegion\":\"us-east-1\",\"eventTime\":\"2015-08-05T05:57:08.258Z\",\"eventName\":\"ObjectCreated:Put\",\"userIdentity\":{\"principalId\":\"AWS:AIDAIZVNNBY7BD57QQT6E\"},\"requestParameters\":{\"sourceIPAddress\":\"10.66.114.16\"},\"responseElements\":{\"x-amz-request-id\":\"9208E84F97830CBE\",\"x-amz-id-2\":\"26tJBP4bvpUVSvdnrQwbsIz2bf/QPlfW+c2FUKFsz3mOCHGmUc03SGFg5a+XaJLx\"},\"s3\":{\"s3SchemaVersion\":\"1.0\",\"configurationId\":\"devops-simple-app-cfn-commit-event\",\"bucket\":{\"name\":\"glbao-virginia\",\"ownerIdentity\":{\"principalId\":\"A1L42QGGP1NZR6\"},\"arn\":\"arn:aws:s3:::glbao-virginia\"},\"object\":{\"key\":\"devops-simple-app-cfn/document-root/document-root.tar.gz\",\"size\":11275,\"eTag\":\"d74372e760e69c4a6a509ff6c1e2623e\",\"sequencer\":\"0055C1A5B3B692D32B\"}}}]}",
 */
@SuppressWarnings("restriction")
public class DIYBuildServerChina {
	
	// Message Title
	public final static String SUBJECT_CDelivery_STEPS = "Continuous Delivery Steps [devops-simple-app-cfn]";
	
	// Communication Channel
	public final static String S3_LISTENING_QUEUE_NAME = "build-server-queue";			// Replace with your own queue
	public final static String NOTIFICATION_ONLY_TOPIC_NAME = "devops-notification";	// Replace with your own topic for sending out message
	
	/*
	 * Application Deployment Registry Schema:
	 * pk:version, eTag
	 * My demo dynamodb ONLY tracks current version
	 */
	public final static String REGISTRY = "devops-simple-app-cfn-reg";
	public final static String REG_PK_NAME = "version";
	
	// Application package source
	public final static String REPO_BUCKET_NAME = "glbao-beijing";	// Replace with your own code package repository
	public final static String REPO_KEY = "devops-simple-app/document-root-china/document-root-china.tar.gz"; // Replace with your own application package key
	public final static String REPO_EVENT_NAME = "devops-simple-app-commit-event";
	
	// AMI baker's user-data
	public final static String AMI_BAKER_USERDATA = "#!/bin/bash\n"
			+ "su - ec2-user -c \"mkdir ~/.aws\"\n"
			+ "su - ec2-user -c \"echo [default] > ~/.aws/credentials\"\n"
			+ "su - ec2-user -c \"echo region = cn-north-1 >> ~/.aws/credentials\"\n"
			+ "IID=`curl http://169.254.169.254/latest/meta-data/instance-id`\n"
			+ "su - ec2-user -c \"aws ec2 create-tags --resources ${IID} --tags Key=Name,Value=ami-baker-for-devops-simple-app\"\n"
			+ "yum -y update\n"
			+ "yum -y install perl-libwww-perl\n"
			+ "yum -y install httpd\n" + "yum -y update\n"
			+ "su - ec2-user -c \"aws s3 cp s3://" + REPO_BUCKET_NAME + "/"
			+ REPO_KEY + " /home/ec2-user/app.tar.gz\"\n"
			+ "tar xzvf /home/ec2-user/app.tar.gz -C /var/www/html/\n"
			+ "service httpd restart\n" + "chkconfig httpd on\n";
	
	// AMI baker's properties
	public final static String AMI_BAKER_SG_ID = "sg-252fde40"; // Replace with your own AMI baker AMI
	public final static String AMI_BAKER_KEY_NAME = "guangleiKeyPair_Chn_Webserver"; // Replace with your own key pair
	public final static String AMI_BAKER_SUBNET_ID = "subnet-ab2335c9"; // Replace with your own subnet ID
	public final static String AMI_BAKER_AMI_ID = "ami-f0821fc9"; // Replace with your own base AMI
	public final static String AMI_BAKER_INSTANCE_PROFILE_ARN = "arn:aws-cn:iam::<ACCOUNT>:instance-profile/build-server-role"; // Replace with your own EC2 role
	public final static InstanceType AMI_BAKER_INSTANCE_TYPE = InstanceType.T2Micro;
	
	// Decode S3 application package arrivals notification
	public final static String EVENT_SOURCE = "eventSource";
	public final static String USER_ID = "userIdentity";
	public final static String RESPONSE_ELEMENTS = "responseElements";
	public final static String EVENT_VERSION = "eventVersion";
	public final static String EVENT_NAME = "eventName";
	public final static String EVENT_TIME = "eventTime";
	public final static String AWS_REGION = "awsRegion";
	public final static String S3 = "s3";
	public final static String OBJECT = "object";
	public final static String CONFIG_ID = "configurationId";
	public final static String KEY = "key";
	public final static String ETAG = "eTag";
	
	// Delivery work flow CloudWatch interface
	private final static String CW_NAMESPACE = "devops";
	private final static String CW_METRIC_DELIVERY_START = "Continuous Delivery Start Count";
	private final static String CW_METRIC_SAME_ETAG = "Same ETag Count";
	private final static String CW_METRIC_PORT_ERROR = "Port Timeout Count";
	private final static String CW_METRIC_INDEX_MISSING = "Index.html Corrupted Count";
	private final static String CW_METRIC_IMAGE_MISSING = "aws.png Missing Count";
	private final static String CW_METRIC_DELIVERY_SUCCESS = "Continuous Delivery Success Count";
	private final static String CW_METRIC_DELIVERY_FAILURE = "Continuous Delivery Failure Count";
	private final static String CW_DIMENSION_NAME = "Application";
	private final static String CW_DIMENSION_VALUE = "Simple Web Server";
	
	// The region.
	Regions regions = Regions.CN_NORTH_1;
	
	// PRIVATE ATTRIBUTES.
	private boolean deliverySuccess = false;
	private String webserverImageId;
	private AmazonEC2 ec2;
	private AmazonSQS sqs;
	private AmazonSNS sns;
	private AmazonDynamoDB ddb;
	private AmazonCloudWatch cw;
	
	// Report interesting data point to CloudWatch
	private void report(String metricName){
		MetricDatum md = new MetricDatum()
			.withMetricName(metricName)
			.withDimensions(new Dimension().withName(CW_DIMENSION_NAME).withValue(CW_DIMENSION_VALUE))
			.withUnit(StandardUnit.Count)
			.withValue(1.0)
			.withTimestamp(new Date());
		PutMetricDataRequest pdr = new PutMetricDataRequest().withNamespace(CW_NAMESPACE);
		ArrayList<MetricDatum> metricCollection = new ArrayList<MetricDatum>();
		metricCollection.add(md);
		pdr.setMetricData(metricCollection);
		cw.putMetricData(pdr);
	}
	
	// We need SQS, DDB, SNS and EC2 services.
	public DIYBuildServerChina() {
		InstanceProfileCredentialsProvider ipcp =  new InstanceProfileCredentialsProvider();
		Region region = Region.getRegion(regions);
		this.ec2 = new AmazonEC2Client(ipcp);
		this.ec2.setRegion(region);
		this.sqs = new AmazonSQSClient(ipcp);
		this.sqs.setRegion(region);
		this.ddb = new AmazonDynamoDBClient(ipcp);
		this.ddb.setRegion(region);
		this.sns = new AmazonSNSClient(ipcp);
		this.sns.setRegion(region);
		this.cw = new AmazonCloudWatchClient(ipcp);
		this.cw.setRegion(region);
	}
	
	// Main Continuous Delivery Logic
	private boolean continuousDelivery() {
		String amiBakerInstanceId = null;
		System.out.println("Any commit?");
		String queueUrl = "https://sqs." + this.regions.getName()
				+ ".amazonaws.com.cn/" + "<ACCOUNT>" + "/" + S3_LISTENING_QUEUE_NAME;	// Replace the account name with your own
		ReceiveMessageRequest rmr = new ReceiveMessageRequest();
		rmr.setQueueUrl(queueUrl);
		rmr.setMaxNumberOfMessages(1); // One message one time.
		List<Message> messages = this.sqs.receiveMessage(rmr).getMessages();
		for (Message message : messages) {
			JSONObject jo = null;
			JSONObject record = null;
			boolean falseAlarm = true;
			System.out.println(message.getBody());
			try {
				jo = new JSONObject(message.getBody());
				//String subject = jo.getString("Subject");
				record = new JSONObject(jo.getString("Records").replace("[", "")
								.replace("]", ""));
				if (record.getJSONObject(S3).getString(CONFIG_ID)
								.equals(REPO_EVENT_NAME)
						&& record
								.getJSONObject(S3)
								.getJSONObject(OBJECT)
								.getString(KEY)
								.equals(REPO_KEY)) {
					String etag = record.getJSONObject(S3)
							.getJSONObject(OBJECT).getString(ETAG);
					
					// Check to see if the ETag is different.
					Map<String, AttributeValue> item = this
							.getItemByPkHashString(this.ddb, REGISTRY,
									REG_PK_NAME, "current", true);
					if (item.get(ETAG) == null) {
						falseAlarm = false;
						this.notify(this.sns, SUBJECT_CDelivery_STEPS,
								"Greenfield detected. ETag: " + etag);
					} else {
						String etagInRegistry = item.get(ETAG).getS();
						if (etagInRegistry != null
								&& etagInRegistry.equals(etag)) {
							this.notify(this.sns, SUBJECT_CDelivery_STEPS,
									"*WARNING* Delivery is  NOT initialized, ETag unchanged.");
							this.report(this.CW_METRIC_SAME_ETAG);
						} else {
							falseAlarm = false;
						}
					}
					// Begin Delivery.
					if (!falseAlarm) {
						// Step #1: Start ec2.
						this.notify(this.sns, SUBJECT_CDelivery_STEPS,
								"Step #1 -> Start new AMI Baker Instance"
										+ " for application ETag: " + etag
										+ ", with userdata: "
										+ AMI_BAKER_USERDATA);
						this.report(this.CW_METRIC_DELIVERY_START);
						amiBakerInstanceId = this.runAmiBaker(this.ec2);

						// Step #2: Test the build.
						this.notify(this.sns, SUBJECT_CDelivery_STEPS,
								"Step #2 -> Test the Build on AMI Baker Instance " + amiBakerInstanceId);
						DescribeInstancesRequest dir = new DescribeInstancesRequest()
								.withInstanceIds(amiBakerInstanceId);
						String privateIp = this.ec2.describeInstances(dir)
								.getReservations().get(0).getInstances().get(0)
								.getPrivateIpAddress();
						ArrayList<URL> urls = new ArrayList<URL>();
						try {
							urls.add(new URL("http://" + privateIp + "/"));
							urls.add(new URL("http://" + privateIp
									+ "/index.html"));
							urls.add(new URL("http://" + privateIp + "/aws.png"));
						} catch (MalformedURLException e) {
							// Will NOT happen.
							e.printStackTrace();
						}
						// Wait for the 80 port to open.
						boolean portNotOpen = true;
						int maxRetry = 300;
						while (portNotOpen) {
							try {
								System.out.println("Try http://" + privateIp
										+ "/");
								URLConnection conn = urls.get(0)
										.openConnection();
								conn.connect();
								portNotOpen = false;
							} catch (IOException e) {
								// e.printStackTrace();
								System.out
										.println("Port 80 not open yet, waiting and retry..."
												+ --maxRetry + " left.");
								if (maxRetry == 0) {
									this.notify(this.sns,
											SUBJECT_CDelivery_STEPS,
											"*WARNING* Port 80 still NOT open after maximum retries. Delivery aborted.");
									this.report(this.CW_METRIC_PORT_ERROR);
									this.report(this.CW_METRIC_DELIVERY_FAILURE);
									this.terminateAmiBaker(this.ec2, amiBakerInstanceId);
									this.deleteTask(this.sqs, message, queueUrl);
									break;
								}
							} finally {
								try {
									Thread.sleep(10 * 1000);
								} catch (InterruptedException e) {
									// Will NOT happen.
									e.printStackTrace();
								}
							}
						}
						if (maxRetry == 0) {
							break;
						}
						// Test the real thing.
						try {
							System.out.println("Try http://" + privateIp
									+ "/index.html");
							BufferedReader br = new BufferedReader(
									new InputStreamReader(urls.get(1)
											.openConnection().getInputStream()));
							String lineOne = br.readLine();
							br.close();
							if (!lineOne.startsWith("<html>")) {
								throw new IOException("index.html corrupted.");
							}
							System.out
									.println("index.html LineOne: " + lineOne);
						} catch (IOException e) {
							// Terminate the CI and notify developer.
							e.printStackTrace();
							this.notify(
									this.sns,
									SUBJECT_CDelivery_STEPS,
									"*WARNING* index.html file cannot be accessed. Delivery aborted. Missile is aiming the last committer, awaiting your order commander.");
							this.report(this.CW_METRIC_INDEX_MISSING);
							this.report(this.CW_METRIC_DELIVERY_FAILURE);
							this.terminateAmiBaker(this.ec2, amiBakerInstanceId);
							this.deleteTask(this.sqs, message, queueUrl);
							break;
						}
						try {
							System.out.println("Try http://" + privateIp
									+ "/aws.png");
							if (urls.get(2).getContent() instanceof URLImageSource) {
								this.notify(this.sns, SUBJECT_CDelivery_STEPS,
										"Step #2.1 -> Build test passed.");
							}
						} catch (IOException e) {
							// Terminate the CI and notify developer.
							e.printStackTrace();
							this.notify(
									this.sns,
									SUBJECT_CDelivery_STEPS,
									"*WARNING* aws.png file cannot be accessed. Delivery aborted. Missile is aiming the last committer, awaiting your order commander.");
							this.report(this.CW_METRIC_IMAGE_MISSING);
							this.report(this.CW_METRIC_DELIVERY_FAILURE);
							this.terminateAmiBaker(this.ec2, amiBakerInstanceId);
							this.deleteTask(this.sqs, message, queueUrl);
							break;
						}
						// Create webserver AMI.
						CreateImageRequest createImageRequest = new CreateImageRequest()
								.withDescription("For [devops-simple-app-cfn]")
								.withInstanceId(amiBakerInstanceId).withNoReboot(false)
								.withName("devops-simple-app-"+this.getTimeBasedSuffix());
						this.webserverImageId = this.ec2.createImage(
								createImageRequest).getImageId();
						this.notify(
								this.sns,
								DIYBuildServerChina.SUBJECT_CDelivery_STEPS,
								"Step #3 -> Create Webserver AMI");
						String imageState = "pending";
						while (!imageState.equals("available")) {
							imageState = ec2
									.describeImages(
											new DescribeImagesRequest()
													.withImageIds(this.webserverImageId))
									.getImages().get(0).getState();
							System.out.println("Webserver AMI creation state: "
									+ imageState);
							try {
								Thread.sleep(1000 * 10);
							} catch (InterruptedException e) {
								// Will NOT reach here.
								e.printStackTrace();
							}
						}
						this.notify(
								this.sns,
								DIYBuildServerChina.SUBJECT_CDelivery_STEPS,
								"Step #4 -> New webserver AMI is ready: "
										+ this.webserverImageId);
						// Step Final: Finalize.
						Map<String, AttributeValue> newItem = new HashMap<String, AttributeValue>();
						newItem.put(REG_PK_NAME,
								new AttributeValue().withS("current"));
						newItem.put(ETAG, new AttributeValue().withS(etag));
						this.putItemString(this.ddb, REGISTRY, newItem);
						this.notify(sns, SUBJECT_CDelivery_STEPS,
								"Step #5 -> Discard AMI Baker.");
						this.terminateAmiBaker(this.ec2, amiBakerInstanceId);
						this.deliverySuccess = true;
						this.notify(this.sns, DIYBuildServerChina.SUBJECT_CDelivery_STEPS, "Step ## -> Delivery Finished. New AMI: "+this.webserverImageId);
						this.report(this.CW_METRIC_DELIVERY_SUCCESS);
					}
				}
			} catch (JSONException e) {
				// Wrong JSON Message Format
				e.printStackTrace();
			} catch (AmazonServiceException e){
				e.printStackTrace();
			}
			falseAlarm = true;
			this.deleteTask(this.sqs, message, queueUrl);
		}
		return deliverySuccess;
	}

	private void deleteTask(AmazonSQS sqs, Message message, String queueUrl) {
		String receiptHandle = message.getReceiptHandle();
		sqs.deleteMessage(new DeleteMessageRequest().withQueueUrl(queueUrl)
				.withReceiptHandle(receiptHandle));
		System.out.println("Deleted Message with handle: " + receiptHandle);
	}

	private void listenForCodeCommit() {
		while (true) {
			try {
				Thread.sleep(10 * 1000);
				this.continuousDelivery();
				this.clear();
			} catch (InterruptedException e) {
				// Should NOT reach here.
				e.printStackTrace();
			}

		}
	}

	private void clear() {
		this.webserverImageId = null;
		this.deliverySuccess = false;
	}

	private Map<String, AttributeValue> getItemByPkHashString(
			AmazonDynamoDB ddb, String tableName, String pkName,
			String pkValue, boolean consistentRead) {
		HashMap<String, AttributeValue> key = new HashMap<String, AttributeValue>();
		key.put(pkName, new AttributeValue().withS(pkValue));
		GetItemRequest req = new GetItemRequest().withTableName(tableName)
				.withKey(key).withConsistentRead(consistentRead)
				.withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
		GetItemResult res = ddb.getItem(req);
		return res.getItem();
	}

	private void putItemString(AmazonDynamoDB ddb, String tableName,
			Map<String, AttributeValue> item) {
		PutItemRequest req = new PutItemRequest().withTableName(tableName)
				.withItem(item)
				.withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
		ddb.putItem(req);
	}

	private void notify(AmazonSNS sns, String subject, String body) {
		String topicArn = "arn:aws-cn:sns" + ":cn-north-1:" + "<ACCOUNT>" + ":"
				+ NOTIFICATION_ONLY_TOPIC_NAME;
		PublishRequest pr = new PublishRequest().withTopicArn(topicArn)
				.withSubject(subject).withMessage(body);
		sns.publish(pr);
		System.out.println("Subject: " + subject + ": " + body);
		this.wait(7);
	}

	private void terminateAmiBaker(AmazonEC2 ec2, String instanceId) {
		ec2.terminateInstances(new TerminateInstancesRequest()
				.withInstanceIds(instanceId));
	}

	private String runAmiBaker(AmazonEC2 ec2) {
		String userdata = this.base64Encode(AMI_BAKER_USERDATA);
		IamInstanceProfileSpecification profileSpec = new IamInstanceProfileSpecification()
				.withArn(AMI_BAKER_INSTANCE_PROFILE_ARN);
		RunInstancesRequest request = new RunInstancesRequest()
				.withImageId(AMI_BAKER_AMI_ID).withUserData(userdata)
				.withKeyName(AMI_BAKER_KEY_NAME)
				.withSubnetId(AMI_BAKER_SUBNET_ID)
				.withSecurityGroupIds(AMI_BAKER_SG_ID)
				.withIamInstanceProfile(profileSpec).withMinCount(1)
				.withMaxCount(1).withInstanceType(AMI_BAKER_INSTANCE_TYPE);
		RunInstancesResult result = ec2.runInstances(request);
		String instanceId = result.getReservation().getInstances().get(0)
				.getInstanceId();
		DescribeInstanceStatusRequest descRequest = new DescribeInstanceStatusRequest()
				.withInstanceIds(instanceId);
		DescribeInstanceStatusResult descResult = null;
		List<InstanceStatus> instanceStatuses = null;
		List<String> instanceStatusesStr = null;
		boolean con = true;
		int tn = 0;
		while (con) {
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			descResult = ec2.describeInstanceStatus(descRequest);
			instanceStatuses = descResult.getInstanceStatuses();
			instanceStatusesStr = new ArrayList<String>();
			for (InstanceStatus instanceStatus : instanceStatuses) {
				System.out.println("#" + (++tn) + ": "
						+ instanceStatus.getInstanceStatus());
				instanceStatusesStr.add(instanceStatus.getInstanceStatus()
						.getStatus());
			}

			if (instanceStatusesStr != null
					&& instanceStatusesStr.size() >= 1
					&& !instanceStatusesStr
							.contains(new String("initializing"))) {
				con = false;
			}
		}
		return instanceId;
	}

	@SuppressWarnings("restriction")
	private String base64Encode(String plain) {
		byte[] b = null;
		String base64 = null;
		try {
			b = plain.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		if (b != null) {
			base64 = new BASE64Encoder().encode(b);
		}
		return base64;
	}
	
	private String getTimeBasedSuffix(){
		return (new Date()).toString().replaceAll(" ","").replaceAll(":","");
	}
	
	private void wait(int secs){
		try {
			Thread.sleep(secs*1000);
		} catch (InterruptedException e) {
			// Will NOT hit this.
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		DIYBuildServerChina server = new DIYBuildServerChina();
		System.out
				.println("Build server is listening on s3://glbao-beijing/devops-simple-app");
		server.listenForCodeCommit();
	}

}

