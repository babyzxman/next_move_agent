package org.gable.blendata.nextmove.shared.constant;

public class ServiceConst {

    public enum ServiceId {
        ZERO_CONTROL("zero-size-control-service")
        , NONE_CONTROL("none-control-service");
        private String val;
        ServiceId(String val){
            this.val = val;
        }
        public String val(){return this.val;}
    }

    public enum ServiceUrl{
        TRANSFER("/api/manage-file/transfer")
        , STOP_ALL_TASK("/api/manage-file/transfer/stop");
        private String val;
        ServiceUrl(String val){
            this.val = val;
        }
        public String val(){return this.val;}
    }
}
