 /**
 *  OwnTracks Sensor (v.0.0.4)
 *
 * MIT License
 *
 * Copyright (c) 2019 fison67@nate.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
 
import groovy.json.JsonSlurper

metadata {
	definition (name: "OwnTracks Sensor", namespace: "fison67", author: "fison67") {
      	capability "Sensor"
	}

	simulator {}
    
    preferences {
        input name: "baseAddressCheck", title:"Address Check ON/OFF" , type: "enum", required: true, options: ["on", "off"], defaultValue: "off"
        input name: "baseAddress1", title:"Base Address #1 (경기도, 서울특별시)" , type: "string", required: false
        input name: "baseAddress2", title:"Base Address #2 (안양시)" , type: "string", required: false
        input name: "baseAddress3", title:"Base Address #3 (만안구, 중구)" , type: "string", required: false
        input name: "baseAddress4", title:"Base Address #4 (안양동, 태평로1가)" , type: "string", required: false
    }

	tiles {
        valueTile("address", "device.address", decoration: "flat", width: 4, height: 1) {
            state "default", label:'${currentValue}'
        }
        childDeviceTiles("all")
       
	}

}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def setStatus(jsonObj){
    log.debug jsonObj
    
    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    
    def type = jsonObj.topic.split("/")[3]
    switch(type){
    case "event":
        checkAddress(jsonObj.event, jsonObj.lat, jsonObj.lon, jsonObj.desc)
    	break
    }
    
    sendEvent(name: "lastCheckin", value: now, displayed: false)
}

def setAPIKey(key){
	state.apiKey = key
}

def isAddressFunctionOn(){
	def val = settings.baseAddressCheck
    if(val == null || val == ""){
    	return false
    }
    return val == "on" ? true : false
}

def checkAddress(event, lat, lon, target){
log.debug target
	if(isAddressFunctionOn() && state.apiKey != null && state.apiKey != ""){
        def params = [
            uri: "https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lon}&language=ko&key=${state.apiKey}"
        ]
        try {
            httpGet(params) { resp ->
                def address = resp.data.results[0].formatted_address
                def addressOK = true
                def errMessage = ""
                if(baseAddressCheck == "on"){
                    def list = resp.data.results[0].address_components
                    list.each { item ->
                        if(item.types.contains("administrative_area_level_1")){
							if(baseAddress1 != "" && item.short_name != baseAddress1){
                            	addressOK = false
                                errMessage = "Base#1 ${baseAddress1} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("locality")){
							if(baseAddress2 != "" && item.short_name != baseAddress2){
                            	addressOK = false
                                errMessage = errMessage + "\nBase#2 ${baseAddress2} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("sublocality_level_1")){
							if(baseAddress3 != "" && item.short_name != baseAddress3){
                            	addressOK = false
                                errMessage = errMessage + "\nBase#3 ${baseAddress3} / ${item.short_name}" 
                            }
                        }else if(item.types.contains("sublocality_level_2")){
							if(baseAddress4 != ""){
                        		def _baseAddr = baseAddress4.split(",")
                                def _subAddrOK = false
                                _baseAddr.each{ _addr->
                                	if(item.short_name.indexOf(_addr) > -1){
                                    	_subAddrOK = true
                                    }
                                }
                            	addressOK = _subAddrOK
                                errMessage = errMessage + "\nBase#4 ${baseAddress4} / ${item.short_name}" 
                            }
                        }
                    }
                    log.debug "Address check >> ${addressOK}"
                }
                
    			sendEvent(name: "address", value: address)
                if(addressOK){
                	setCurrentValue(event, target)
                }else{
                	state.errCount = state.errCount + 1
                    if(state.errCount == 100){
                        state.errCount = 0
                    }
                    def msg = "Address is wrong!!! ${errMessage} #${state.errCount}"
                    log.error msg
                    sendEvent(name: "errorAddress", value: msg)
                }
            }
        } catch (e) {
            log.error "something went wrong: $e"
        }
    }else{
    	setCurrentValue(event, target)
    }
    
}

def setCurrentValue(event, target){
    def childName = "${device.deviceNetworkId}_${target}"
	def child = childDevices.find { it.deviceNetworkId == childName }
    if(!child){
    	def childDevice =  addChildDevice("OwnTracks Child Sensor", childName , null, [completedSetup: true, label: "${device.label} ${target}", isComponent: false])
    }else{
    	child.setStatus(event)
    }
}

def updated() {

}
