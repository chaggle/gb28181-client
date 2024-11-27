package cn.gb.gb28181.service;

import cn.gb.gb28181.conf.Cache;
import cn.gb.gb28181.stream.FfmpegStream;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSResourceResolver;

import javax.annotation.Resource;
import javax.sdp.MediaDescription;
import javax.sdp.Origin;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import javax.sip.*;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

@Component
@Slf4j
public class RequestProcessor {

    private MessageFactory messageFactory;
    private ServerTransaction inviteTid;
    private Request inviteRequest;
    private Dialog dialog;

    public RequestProcessor() throws PeerUnavailableException {
        messageFactory = SipFactory.getInstance().createMessageFactory();
    }

    public void process(RequestEvent requestReceivedEvent, Dialog dialog) {
        this.dialog = dialog;

        Request request = requestReceivedEvent.getRequest();
        ServerTransaction serverTransactionId = requestReceivedEvent.getServerTransaction();

        log.debug("Request " + request.getMethod() + " received" + " with server transaction id " + serverTransactionId);

        if (request.getMethod().equals(Request.BYE))
            processBye(request, serverTransactionId);
        else if (request.getMethod().equals(Request.INVITE))
            processInvite(requestReceivedEvent, serverTransactionId);
        else if (request.getMethod().equals(Request.ACK))
            processAck(request, serverTransactionId);
        else if (request.getMethod().equals(Request.CANCEL))
            processCancel(request, serverTransactionId);
    }

    public void processBye(Request request,
                           ServerTransaction serverTransactionId) {
        try {
            log.debug("got a bye");
            if (serverTransactionId == null) {
                log.debug("null TID.");
                return;
            }
            Dialog dialog = serverTransactionId.getDialog();
            log.debug("Dialog State = " + dialog.getState());
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            log.debug("Sending OK.");
            log.debug("Dialog State = " + dialog.getState());

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);

        }
    }


    /**
     * Process the invite request.
     */
    public void processInvite(RequestEvent requestEvent, ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            log.debug("Got an Invite sending Trying");

            FromHeader from = (FromHeader) request.getHeader("From");
            log.debug("Call from: " + from.getAddress().getDisplayName() + "/" + from.getAddress().getURI());

            ContactHeader contact = (ContactHeader) request.getHeader("Contact");
            log.debug("Contact: " + contact.getAddress().getDisplayName() +"/"+ contact.getAddress().getURI());

            String message = StrUtil.str(request.getRawContent(), StandardCharsets.UTF_8);
            // jainSip不支持y= f=字段， 移除以解析。
            int ssrcIndex = message.indexOf("y=");
            int mediaDescriptionIndex = message.indexOf("f=");
            // 检查是否有y字段
            SessionDescription sdp;
            String ssrc = null;
            String mediaDescription = null;
            if (mediaDescriptionIndex == 0 && ssrcIndex == 0) {
                sdp = SdpFactory.getInstance().createSessionDescription(message);
            } else {
                String[] lines = message.split("\\r?\\n");
                StringBuilder sdpBuffer = new StringBuilder();
                for (String line : lines) {
                    if (line.trim().startsWith("y=")) {
                        ssrc = line.substring(2);
                    } else if (line.trim().startsWith("f=")) {
                        mediaDescription = line.substring(2);
                    } else {
                        sdpBuffer.append(line.trim()).append("\r\n");
                    }
                }
                sdp = SdpFactory.getInstance().createSessionDescription(sdpBuffer.toString());
            }
            MediaDescription md = (MediaDescription) sdp.getMediaDescriptions(true).get(0);

            Cache.cacheObj.put("host",sdp.getConnection().getAddress());
            Cache.cacheObj.put("port", md.getMedia().getMediaPort());

            Response response = messageFactory.createResponse(Response.TRYING, request);
            ServerTransaction st = requestEvent.getServerTransaction();

            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
            }
            dialog = st.getDialog();

            st.sendResponse(response);

            Response okResponse = messageFactory.createResponse(Response.OK, request);
            okResponse.setHeader(contact);

            ToHeader toHeader = (ToHeader) okResponse.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // Application is supposed to set.

            SessionDescription toSdp = SdpFactory.getInstance().createSessionDescription();
            Vector<MediaDescription> mediaDescriptions = new Vector<>();
            mediaDescriptions.add(
                    SdpFactory.getInstance().createMediaDescription("video",1, 1, "RTP/AVP", new int[] {96, 98, 97})
            );
            toSdp.setConnection(SdpFactory.getInstance().createConnection("IN", "IP4", "192.168.31.7"));
            toSdp.setMediaDescriptions(mediaDescriptions);
            Origin origin = SdpFactory.getInstance().createOrigin("34020000002000000002", "192.168.31.6");
            toSdp.setOrigin(origin);

            ContentTypeHeader contentTypeHeader = SipFactory.getInstance().createHeaderFactory().createContentTypeHeader("APPLICATION", "SDP");

            StringBuffer content = new StringBuffer(StrUtil.str(toSdp, Charset.defaultCharset()));
            content.append("a=recvonly\r\n")
                    .append("a=rtpmap:96 PS/90000\r\n")
                    .append("a=rtpmap:98 H264/90000\r\n")
                    .append("a=rtpmap:97 MPEG4/90000\r\n")
                    .append("y=0000001003\r\n")
                    .append("f=");
           okResponse.setContent(content, contentTypeHeader);

            inviteTid = st;
            // Defer sending the OK to simulate the phone ringing.
            inviteRequest = request;

            try {
                if (inviteTid.getState() != TransactionState.COMPLETED) {
                    inviteTid.sendResponse(okResponse);
                }
            } catch (SipException ex) {
                ex.printStackTrace();
            } catch (InvalidArgumentException ex) {
                ex.printStackTrace();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }

    public void processCancel(Request request,
                              ServerTransaction serverTransactionId) {
        try {
            log.debug("shootme:  got a cancel.");
            if (serverTransactionId == null) {
                log.debug("shootme:  null tid.");
                return;
            }
            Response response = messageFactory.createResponse(200, request);
            serverTransactionId.sendResponse(response);
            if (dialog.getState() != DialogState.CONFIRMED) {
                response = messageFactory.createResponse(
                        Response.REQUEST_TERMINATED, inviteRequest);
                inviteTid.sendResponse(response);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);

        }
    }

    /**
     * Process the ACK request. Send the bye and complete the call flow.
     */
    public void processAck(Request request,
                           ServerTransaction serverTransaction) {
        String url = "/Users/chaggle/Downloads/photo/2024-11-27.mp4";
        String ip = Cache.cacheObj.get("host").toString();
        String port  = Cache.cacheObj.get("port").toString();
        String stream = "rtp://" + ip + ":" + port;
        FfmpegStream.push(url, stream);
    }
}