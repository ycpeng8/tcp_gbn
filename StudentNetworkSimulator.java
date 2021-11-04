import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

public class StudentNetworkSimulator extends NetworkSimulator 
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                    Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     * Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here. Remember, you cannot use
    // these variables to send messages error free! They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    /** statistical variables  **/
    private int Num_originalPkt_transBy_A = 0;
    private int Num_retransBy_A = 0;
    private int Num_delivered_to_Layter5_atB = 0;
    private int Num_Ackpkt_sentBy_B = 0;
    private int Num_corrupted_pkt = 0;
    private double loss_ratio = 0;
    private double corrupted_ratio = 0;

    private Map<Integer,Double> rtt_map = new HashMap<Integer,Double>();
    private double rttCount = 0.0;
    private double total_rtt = 0.0;

    private Map<Integer,Double> commun_Map = new HashMap<Integer,Double>();
    private double communCount = 0;
    private double total_commun = 0.0;
    /**  **/

    /** A's states **/

    private LinkedList<Packet> sender_buffer = new LinkedList<>();
    private LinkedList<Integer> ack_buffer = new LinkedList<>();
    // private Packet[] SWS; //Sender Window
    private int send_base; //index of last unacked packet 
    private int next_seq;//next packet sequnce number
    private int LPS; // Last packet sent


    /*
     * B variables and functions
     */
    private int RWS; // receive window size
    private int NPE; // next packet expected
    private int b_acknum; // b's acknum
    private int b_checksum; // b's checksum
    private int[] sack = {-1, -1, -1, -1, -1}; // b's sack
    private LinkedList<Packet> sack_buffer = new LinkedList<Packet>(); // b's sack buffer

    private void b_send_pkt(int seqnum, int[] sack) {
        b_checksum = seqnum + b_acknum;
        Packet sndpkt = new Packet(seqnum, b_acknum, b_checksum, sack);
        toLayer3(B, sndpkt);
        Num_Ackpkt_sentBy_B++;
        System.out.print("sack: [ ");
        for (int i = 0; i < 5; i++)
        {
            if (sack[i] == -1)
            {
                break;
            }
            System.out.print(sack[i] + " ");
        }
        System.out.println("]");

        return;
    }

    // output checksum
    private int Checksumming(Packet packet) {
        char[] payload = packet.getPayload().toCharArray();
        int checksum = packet.getSeqnum() + packet.getAcknum();
        for (char c : payload) {
            checksum += (int) c;
        }
        return checksum;
    }

    // check if corrupted
    private boolean isCorrupted(Packet packet) {
        return packet.getChecksum() != Checksumming(packet);
    }

    // calculate the offset between send_base_seqnum and acked num
    private int getOffset(int sbq, int ackseq){
        if(ackseq< sbq){
            return LimitSeqNo - sbq + ackseq;
        }
        else if(sbq <ackseq ){
            return ackseq - sbq;
        }
        else{
            return 0;
        }

    }

    // This is the constructor. Don't touch!
    public StudentNetworkSimulator(int numMessages, double loss, double corrupt, double avgDelay, int trace, int seed,
            int winsize, double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send. It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        next_seq = LPS % LimitSeqNo;
        Packet sender_packet = new Packet(next_seq, 0, -1, message.getData());
        sender_packet.setChecksum(Checksumming(sender_packet));
        sender_buffer.add(sender_packet);
        ack_buffer.add(0);
        System.out.println("sender buffer size is " + sender_buffer.size());
        System.out.println("LPS is " + LPS);
        System.out.println("Send_base is " + send_base);
        System.out.println("window siez is " + WindowSize);

        // travers the send window to see if there is any unsent packet then send it.
        for (LPS = send_base; LPS < sender_buffer.size() && LPS < send_base + WindowSize; LPS++) {
            if (sender_buffer.get(LPS) != null && ack_buffer.get(LPS) == 0) { 
                Num_originalPkt_transBy_A++;
                toLayer3(A, sender_buffer.get(LPS));
                rtt_map.put(LPS,getTime());
                commun_Map.put(LPS,getTime());
                ack_buffer.set(LPS,1); // set 1 in ack_buffer to indicate this packet has been sent but not acked yet
                stopTimer(A);
                startTimer(A, RxmtInterval);
            }
        }


    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side. "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        if(Checksumming(packet) == packet.getChecksum()){
            int[] tmpsack = packet.getSack();
            int send_base_Seq = send_base % LimitSeqNo;
            List<Integer> tmpal = Arrays.stream(packet.getSack()).boxed().collect(Collectors.toList());
            int ack = packet.getSeqnum();
            System.out.println("get sack "+tmpal);
            System.out.println("get ack "+ack);
            System.out.println("send_base_Seq "+send_base_Seq);
            System.out.println("send_base before update "+send_base);

            /* GBN send culmulative ack. Update send_abse according to the ack*/
            rttCount++;
            if(ack < send_base_Seq){ // Since the sequence number is wrapped so am the ack number. 
                stopTimer(A);        // The situation that ack number is less than Send base can occur
                for(int i=0;i < tmpsack.length;i++){
                    if(tmpsack[i] == -1){
                        continue;
                    }
                    int offset = getOffset(send_base_Seq,tmpsack[i]);
                    int idx = send_base+offset;
                    ack_buffer.set(idx,2);
                }

                int last_send_base = send_base;
                send_base += LimitSeqNo - send_base_Seq + ack; // update send_base when ack number is less than Send base can occur
                System.out.println("send_base after update "+send_base);
                for(int i=last_send_base;i<send_base && i<ack_buffer.size();i++){//Check the acks in SACK
                    ack_buffer.set(i,2);                                         //update status of the packets that are acked in SACK to 2,  
                                                                                 // meaning this packet has been acked so that it will not be retransmitted
                    double tmptime = rtt_map.get(send_base-1);
                    if(tmptime != -1.0){
                        total_rtt += getTime() - tmptime;
                        rtt_map.put(last_send_base,-1.0);
                        // rttCount++;
                    }
                    total_commun += getTime() - commun_Map.get(last_send_base);
                    communCount++;

                }
            }
            else if(send_base_Seq < ack){ // Normal situation that ack is larger than send_base seq 
                stopTimer(A);
                for(int i=0;i < tmpsack.length;i++){
                    if(tmpsack[i] == -1){
                        continue;
                    }
                    int offset = getOffset(send_base_Seq,tmpsack[i]);
                    System.out.println("offset is "+ offset);
                    int idx = send_base+offset;
                    System.out.println("idx is "+ idx);
                    ack_buffer.set(idx,2);
                    System.out.println("ack buffer is "+ ack_buffer);
                }
                int last_send_base = send_base;
                send_base += ack-send_base_Seq; // update send_base
                System.out.println("send_base after update "+send_base);
                for(int i=last_send_base;i<send_base && i<ack_buffer.size();i++){ //Check the acks in SACK
                    ack_buffer.set(i,2);                                          //update status of the packets that are acked in SACK to 2,  
                                                                                // meaning this packet has been acked so that it will not be retransmitted
                    double tmptime = rtt_map.get(send_base-1);
                    if(tmptime != -1.0){
                        total_rtt += getTime() - tmptime;
                        rtt_map.put(last_send_base,-1.0);
                    }
                    total_commun += getTime() - commun_Map.get(last_send_base);
                    communCount++;

                }
            }
            else{
                for(int i=0;i < tmpsack.length;i++){
                    if(tmpsack[i] == -1){
                        continue;
                    }
                    int offset = getOffset(send_base_Seq,tmpsack[i]);
                    int idx = send_base+offset;
                    ack_buffer.set(idx,2);
                }

            }
        }
        else{
            //get corrupted packet
            System.out.println("get a corrupted ack packet from B");
            Num_corrupted_pkt++;
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        System.out.println("Timer interrupt");
        
        for(int i=send_base;i<LPS;i++){
            if(ack_buffer.get(i)!=2){
                if(ack_buffer.get(i)==0){ //Check if there is any new packet in the sendwindow. If so, number of original packet sent by A adds one
                    Num_originalPkt_transBy_A++;
                    rttCount++;
                    commun_Map.put(i,getTime());
                    communCount++;
                }else{ 
                    Num_retransBy_A++;
                }
                toLayer3(A, sender_buffer.get(i));
                rtt_map.put(i,getTime());
                stopTimer(A);
                startTimer(A, RxmtInterval);
            }
        }
    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        send_base = 0;
        next_seq = 0;
        LPS = 0;
        LimitSeqNo = WindowSize + 1;
    }

    // This routine will be called whenever a packet sent from the A-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side. "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        // if packet is corrupted
        if (isCorrupted(packet))
        {
            System.out.println("bInput(): B getting a corrupted pkt");
            Num_corrupted_pkt++;
            return;
        }

        System.out.println("bInput(): B getting pkt" + packet.getSeqnum() + ", expecting pkt" + NPE);

        int this_seqnum = packet.getSeqnum();

        // if packet is duplicate and ACKed before NPE
        if (((this_seqnum < NPE) && ((NPE - this_seqnum) <= WindowSize)) 
        || ((this_seqnum > NPE) && ((this_seqnum - NPE) >= WindowSize)))
        {
            b_send_pkt(NPE, sack);
            return;
        }

        // if the size of sack is 0, the operation will be easy and I take it out as one part alone
        if (sack_buffer.size() == 0)
        {
            if (this_seqnum == NPE)
            {
                NPE = (NPE + 1) % LimitSeqNo;
                b_send_pkt(NPE, sack);
                toLayer5(packet.getPayload());
                Num_delivered_to_Layter5_atB ++;
                return;
            }
            else
            {
                sack_buffer.add(packet);
                sack[0] = packet.getSeqnum();
                b_send_pkt(NPE, sack);
                return;
            }
        }
        // if the sack is full and this_seqnum doesn't equal to NPE, just drop it and send ACK
        else if ((this_seqnum != NPE) && (sack_buffer.size() == 5))
        {
            b_send_pkt(NPE, sack);
            return;
        }
        // if the sack is not full
        else
        {
            // if receive the expected packet, send cum ACK with an appropriate SACK
            if (this_seqnum == NPE)
            {
                NPE = (NPE + 1) % LimitSeqNo;
                toLayer5(packet.getPayload());
                Num_delivered_to_Layter5_atB ++;
                while (sack_buffer.size() != 0)
                {
                    if (sack_buffer.get(0).getSeqnum() != NPE)
                    {
                        break;
                    }
                    
                    toLayer5(packet.getPayload());
                    Num_delivered_to_Layter5_atB ++;
                    NPE = (NPE + 1) % LimitSeqNo; 
                    sack_buffer.remove();
                }

                // set sack
                if (sack_buffer.size() == 0)
                {
                    for (int i = 0; i < 5; i++)
                    {
                        sack[i] = -1;
                    }
                }
                else
                {
                    int length1 = sack_buffer.size();
                    for (int i = 0; i < 5; i++)
                    {
                        if (i < length1)
                        {
                            sack[i] = sack_buffer.get(i).getSeqnum();
                        }
                        sack[i] = -1;
                    }
                }

                b_send_pkt(NPE, sack);
                return;
            }
            // if receive the unexpected packet, send ACK NPE with a changed SACK
            else
            {
                // set sack_buffer
                int sack_buffer_length = sack_buffer.size();
                for (int i = 0; i < sack_buffer_length; i++)
                {
                    // if packet is duplicate and ACKed after NPE
                    if (this_seqnum == sack_buffer.get(i).getSeqnum())
                    {
                        b_send_pkt(NPE, sack);
                        return;
                    }

                    if ((this_seqnum > NPE) 
                    && (((sack_buffer.get(i).getSeqnum() > NPE) && (this_seqnum < sack_buffer.get(i).getSeqnum())) 
                    || (sack_buffer.get(i).getSeqnum() < NPE)))
                    {
                        sack_buffer.add(i, packet);
                        break;
                    }
                    else if ((this_seqnum < NPE) 
                    && ((sack_buffer.get(i).getSeqnum() < NPE) 
                    || (this_seqnum < sack_buffer.get(i).getSeqnum())))
                    {
                        sack_buffer.add(i, packet);
                        break;
                    }

                }
                if (sack_buffer.size() == sack_buffer_length)
                {
                    sack_buffer.addLast(packet);
                }

                // set sack
                int length2 = sack_buffer.size();
                for (int i = 0; i < 5; i++)
                {
                    if (i < length2)
                    {
                        sack[i] = sack_buffer.get(i).getSeqnum();
                    }
                    sack[i] = -1;
                }
                b_send_pkt(NPE, sack);
                return;
            }
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        RWS = 1;
        NPE = 0;
        b_acknum = 1;
    }

    // Use to print final statistics
    protected void Simulation_done() {
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO
        // NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        double  Ratio_lost = 0;
        if(Num_retransBy_A - Num_corrupted_pkt < 0){
            Ratio_lost = 0;
        }else{
            Ratio_lost = (double)(Num_retransBy_A - Num_corrupted_pkt)/(double)((Num_originalPkt_transBy_A+Num_retransBy_A)+Num_Ackpkt_sentBy_B);
        }
        double  Ratio_corrupted = (double)Num_corrupted_pkt / (double)((Num_originalPkt_transBy_A+Num_retransBy_A)+ Num_Ackpkt_sentBy_B-(Num_retransBy_A-Num_corrupted_pkt));
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + Num_originalPkt_transBy_A);
        System.out.println("Number of retransmissions by A:" + Num_retransBy_A);
        System.out.println("Number of data packets delivered to layer 5 at B:" + Num_delivered_to_Layter5_atB);
        System.out.println("Number of ACK packets sent by B:" + Num_Ackpkt_sentBy_B);
        System.out.println("Number of corrupted packets:" + Num_corrupted_pkt);
        System.out.println("Ratio of lost packets:" + Ratio_lost);
        System.out.println("Ratio of corrupted packets:" + Ratio_corrupted);
        System.out.println("Average RTT:" + total_rtt/rttCount);
        System.out.println("Average communication time:" + total_commun/communCount);
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        System.out.println("All rtt:" + total_rtt);
        System.out.println("counter for rtt:" + rttCount);
        System.out.println("All communication time:" + total_commun);
        System.out.println("counter for communication:" + communCount);
        // EXAMPLE GIVEN BELOW
        // System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}