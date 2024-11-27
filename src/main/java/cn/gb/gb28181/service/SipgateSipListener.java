package cn.gb.gb28181.service;

import cn.gb.gb28181.conf.SipConfig;
import cn.gb.gb28181.utils.MD5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.*;

@Component
@Slf4j
public class SipgateSipListener implements SipListener {

    private SipConfig sipConfig;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private ClientTransaction registerTid;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;
    private Dialog dialog;
    private ListeningPoint lp;
    private ServerTransaction inviteTid;
    // 本地RTP的推流端口
    private Integer rtpPort;
    private String rtpHost;
    private RequestProcessor requestProcessor;
    private List<ViaHeader> viaHeaders;

    public SipgateSipListener() throws PeerUnavailableException, InvalidArgumentException, ParseException {
        sipConfig = new SipConfig();
        sipConfig.setUsername("34020000002000000002");
        sipConfig.setPassword("12345678");
        sipConfig.setDomain("340200000");
        sipConfig.setProxy("172.20.10.11:5080");
        sipConfig.setDisplayName("chaggle");
        requestProcessor = new RequestProcessor();
    }

    public void init() throws Exception {
        SipFactory sipFactory = SipFactory.getInstance();

        sipStack = null;
        Properties properties = new Properties();
        properties.setProperty("javax.sip.IP_ADDRESS", getLocalIPAddress());
        properties.setProperty("javax.sip.OUTBOUND_PROXY", sipConfig.getProxy() + "/" + ListeningPoint.UDP);
        properties.setProperty("javax.sip.STACK_NAME", "SipConfig Test");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "shootmedebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "shootmelog.txt");

        // Create SipStack object
        sipStack = sipFactory.createSipStack(properties);
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();
        lp = sipStack.createListeningPoint(getLocalIPAddress(), 8002, ListeningPoint.UDP);

        SipgateSipListener listener = this;

        sipProvider = sipStack.createSipProvider(lp);
        sipProvider.addSipListener(listener);

        // Create ViaHeaders
        viaHeaders = new ArrayList<ViaHeader>();
        String ipAddress = lp.getIPAddress();
        ViaHeader viaHeader = headerFactory.createViaHeader(ipAddress, lp.getPort(), lp.getTransport(), null);
        // add via headers
        viaHeaders.add(viaHeader);
    }

    public void register() throws ParseException, InvalidArgumentException, TransactionUnavailableException,
            SipException {
        Request request = packageRegisterRequest();

        // Create the client transaction.
        registerTid = sipProvider.getNewClientTransaction(request);

        // send the request out.
        registerTid.sendRequest();

        dialog = registerTid.getDialog();
    }

    private Request packageRegisterRequest() throws ParseException, InvalidArgumentException {
        // create >From Header
        SipURI fromAddress = addressFactory.createSipURI(sipConfig.getUsername(), getLocalIPAddress());

        Address fromNameAddress = addressFactory.createAddress(fromAddress);
        fromNameAddress.setDisplayName(sipConfig.getDisplayName());
        FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, null);

        // create To Header
        SipURI toAddress = addressFactory.createSipURI(sipConfig.getUsername(), sipConfig.getDomain());
        Address toNameAddress = addressFactory.createAddress(toAddress);
        toNameAddress.setDisplayName(sipConfig.getDisplayName());
        ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

        // create Request URI
        SipURI requestURI = addressFactory.createSipURI(sipConfig.getUsername(), sipConfig.getDomain());

        // Create a new CallId header
        CallIdHeader callIdHeader = sipProvider.getNewCallId();

        // Create a new Cseq header
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.REGISTER);

        // Create a new MaxForwardsHeader
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

        // Create the request.
        Request request = messageFactory.createRequest(requestURI, Request.REGISTER, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);

        // Create contact headers
        SipURI contactUrl = addressFactory.createSipURI(sipConfig.getUsername(), getLocalIPAddress());
        contactUrl.setPort(8002);
        contactUrl.setLrParam();

        // Create the contact name address.
        SipURI contactURI = addressFactory.createSipURI(sipConfig.getUsername(), getLocalIPAddress());
        contactURI.setPort(sipProvider.getListeningPoint(lp.getTransport()).getPort());

        Address contactAddress = addressFactory.createAddress(contactURI);

        // Add the contact address.
        contactAddress.setDisplayName(sipConfig.getDisplayName());

        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        contactHeader.setParameter("expires", "3600");
        request.addHeader(contactHeader);

        // Create UserAgentHeader
        ArrayList<String> productList = new ArrayList<String>(1);
        productList.add("SipListenerSample");
        UserAgentHeader userAgentHeader = headerFactory.createUserAgentHeader(productList);
        request.addHeader(userAgentHeader);
        return request;
    }

    public String getLocalIPAddress() {
        // 可以进行配置化管理
        return "172.20.10.2";
    }

    protected String getIPFromWhatismyip() throws MalformedURLException, IOException {
        URL whatismyip = new URL("http://automation.whatismyip.com/n09230945.asp");
        URLConnection connection = whatismyip.openConnection();
        connection.addRequestProperty("Protocol", "Http/1.1");
        connection.addRequestProperty("Connection", "keep-alive");
        connection.addRequestProperty("Keep-Alive", "1000");
        connection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:12.0) Gecko/20100101 Firefox/12.0");

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String ip = in.readLine(); // you get the IP as a String
        return ip;
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
        try {
            requestProcessor.process(requestReceivedEvent, dialog);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        log.debug("Got a response. Code: " + responseReceivedEvent.getResponse().getStatusCode() + " / CSeq: "
                + responseReceivedEvent.getResponse().getHeader(CSeqHeader.NAME));

        Response response = (Response) responseReceivedEvent.getResponse();
        ClientTransaction tid = responseReceivedEvent.getClientTransaction();
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

        log.debug("Response received : Status Code = " + response.getStatusCode() + " " + cseq);

        if (response.getStatusCode() == Response.UNAUTHORIZED
                || response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED) {
            ProxyAuthenticateHeader proxyAuthenticateHeader = (ProxyAuthenticateHeader) response.getHeader(ProxyAuthenticateHeader.NAME);
            try {
                Request request = packageRegisterRequest();
                ProxyAuthorizationHeader authorizationHeader = headerFactory.createProxyAuthorizationHeader("Digest");
                authorizationHeader.setUsername(sipConfig.getUsername());
                authorizationHeader.setRealm(proxyAuthenticateHeader.getRealm());
                authorizationHeader.setNonce(proxyAuthenticateHeader.getNonce());
                authorizationHeader.setURI(addressFactory.createSipURI(null, sipConfig.getProxy()));
                authorizationHeader.setAlgorithm(proxyAuthenticateHeader.getAlgorithm());
                String res = MD5Util.sipResponse(sipConfig.getUsername(), proxyAuthenticateHeader.getRealm(),
                        sipConfig.getPassword(), proxyAuthenticateHeader.getNonce(), null, Request.REGISTER,
                        "sip" + ":" + sipConfig.getProxy());
                authorizationHeader.setResponse(res);
                request.setHeader(authorizationHeader);
                sipProvider.sendRequest(request);
            } catch (SipException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (InvalidArgumentException | ParseException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            if (response.getStatusCode() == Response.OK) {
                if (cseq.getMethod().equals(Request.REGISTER)) {
                    ContactHeader contactHeader = (ContactHeader) response.getHeader("Contact");
                    Long seconds = (long) contactHeader.getExpires();
                    Long delay = (seconds * 1000) - 100;
                    new Timer().schedule(new MyTimerTask(this), delay);
                    log.debug("Okay. We are registered for next " + seconds + " seconds.");
                } else if (cseq.getMethod().equals(Request.INVITE)) {
                    Dialog dialog = inviteTid.getDialog();
                    Request ackRequest = dialog.createAck(cseq.getSeqNumber());
                    log.debug("Sending ACK");
                    dialog.sendAck(ackRequest);
                } else if (cseq.getMethod().equals(Request.CANCEL)) {
                    if (dialog.getState() == DialogState.CONFIRMED) {
                        // oops cancel went in too late. Need to hang up the
                        // dialog.
                        log.debug("Sending BYE -- cancel went in too late !!");
                        Request byeRequest = dialog.createRequest(Request.BYE);
                        ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                        dialog.sendRequest(ct);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processTimeout(TimeoutEvent arg0) {
        log.debug("Process event recieved.");
    }

    public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
        log.debug("Transaction terminated event recieved.");
    }

    public void processDialogTerminated(DialogTerminatedEvent arg0) {
        log.debug("Dialog terminated event recieved.");
    }

    public void processIOException(IOExceptionEvent arg0) {
        log.debug("IO Exception event recieved.");
    }

    class MyTimerTask extends TimerTask {
        SipgateSipListener listener;

        public MyTimerTask(SipgateSipListener listener) {
            this.listener = listener;

        }

        public void run() {
            log.debug("Reinvite please");
            try {
                listener.register();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }
}