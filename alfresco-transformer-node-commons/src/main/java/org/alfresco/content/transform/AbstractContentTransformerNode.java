package org.alfresco.content.transform;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.alfresco.messaging.MessageConsumer;
import org.alfresco.messaging.MessageProducer;

public abstract class AbstractContentTransformerNode implements MessageConsumer
{
    private static final Log logger = LogFactory.getLog(AbstractContentTransformerNode.class);

    protected ContentTransformerNodeWorker transformerWorker;
    protected MessageProducer messageProducer;
    
    public void setTransformerWorker(ContentTransformerNodeWorker transformerWorker)
    {
        this.transformerWorker = transformerWorker;
    }

    public void setMessageProducer(MessageProducer messageProducer)
    {
        this.messageProducer = messageProducer;
    }
    
    public void onReceive(Object message)
    {
        TransformationRequest request = (TransformationRequest) message;
        logger.info("Processing transformation request " + request.getTransformationRequestId());
        ContentTransformerNodeWorkerProgressReporterImpl progressReporter =
                new ContentTransformerNodeWorkerProgressReporterImpl(request);
        try
        {
            progressReporter.onTransformationStarted();
            
            transformerWorker.transform(
                    request.getSourceContentReference(), 
                    request.getTargetContentReference(), 
                    request.getOptions(),
                    progressReporter);
            
            progressReporter.onTransformationComplete();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public Class<?> getConsumingMessageBodyClass()
    {
        return TransformationRequest.class;
    }
    
    public class ContentTransformerNodeWorkerProgressReporterImpl implements ContentTransformerNodeWorkerProgressReporter
    {
        private TransformationRequest request;
        
        public ContentTransformerNodeWorkerProgressReporterImpl(TransformationRequest request)
        {
            this.request = request;
        }
        
        public void onTransformationStarted()
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Starting transformation of " +
                        "transformationId=" + request.getTransformationRequestId());
            }
            TransformationReply reply = 
                    new TransformationReply(request);
            reply.setStatus(TransformationReply.STATUS_IN_PROGRESS);
            messageProducer.send(reply);
        }
        
        public void onTransformationProgress(float progress)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(progress*100 + "% progress on transformation of " +
                        "transformationId=" + request.getTransformationRequestId());
            }
            TransformationReply reply = new TransformationReply(request);
            reply.setStatus(TransformationReply.STATUS_IN_PROGRESS);
            reply.setProgress(progress);
            messageProducer.send(reply);
        }
        
        public void onTransformationComplete()
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Completed transformation of " +
                        "transformationId=" + request.getTransformationRequestId());
            }
            TransformationReply reply = new TransformationReply(request);
            reply.setStatus(TransformationReply.STATUS_COMPLETE);
            messageProducer.send(reply);
        }
    }

}
