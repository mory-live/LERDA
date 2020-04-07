//
//  ViewController.swift
//  MORY_mvp
//
//  Created by Wataru Yasuda on 2018/11/22.
//  Copyright © 2018年 Wataru Yasuda. All rights reserved.
//

import UIKit
import SkyWay

class ViewController: UIViewController {
    
    fileprivate var peer: SKWPeer?
    fileprivate var mediaConnection: SKWMediaConnection?
    fileprivate var remoteStream: SKWMediaStream?

    @IBOutlet weak var Peer_id: UITextField!
    @IBOutlet weak var remoteStreamView: SKWVideo!
    
    var peerId = ""
    
    @IBAction func Connect_btn(_ sender: Any) {
        peerId = Peer_id.text!
        Peer_id.resignFirstResponder()
        self.call(targetPeerId: peerId)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.setup()
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        self.mediaConnection?.close()
        self.peer?.destroy()
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }

}

// MARK: setup skyway

extension ViewController{
    
    func setup(){
        
        guard let apikey = (UIApplication.shared.delegate as? AppDelegate)?.skywayAPIKey, let domain = (UIApplication.shared.delegate as? AppDelegate)?.skywayDomain else{
            print("Not set apikey or domain")
            return
        }
        
        let option: SKWPeerOption = SKWPeerOption.init();
        option.key = apikey
        option.domain = domain
        
        peer = SKWPeer(options: option)
        
        if let _peer = peer{
            self.setupPeerCallBacks(peer: _peer)
            self.setupStream(peer: _peer)
        }else{
            print("failed to create peer setup")
        }
    }
    
    func setupStream(peer:SKWPeer){
        SKWNavigator.initialize(peer);
        let constraints:SKWMediaConstraints = SKWMediaConstraints()
        //self.localStream = SKWNavigator.getUserMedia(constraints)
        //self.localStream?.addVideoRenderer(self.localStreamView, track: 0)
    }
    
    func call(targetPeerId:String){
        let option = SKWCallOption()
        
        if let mediaConnection = self.peer?.call(withId: targetPeerId, stream: nil/*self.localStream*/, options: option){
            self.mediaConnection = mediaConnection
            self.setupMediaConnectionCallbacks(mediaConnection: mediaConnection)
        }else{
            print("failed to call :\(targetPeerId)")
        }
    }
}

// MARK: skyway callbacks

extension ViewController{
    
    func setupPeerCallBacks(peer:SKWPeer){
        
        // MARK: PEER_EVENT_ERROR
        peer.on(SKWPeerEventEnum.PEER_EVENT_ERROR, callback:{ (obj) -> Void in
            if let error = obj as? SKWPeerError{
                print("\(error)")
            }
        })
        
        // MARK: PEER_EVENT_OPEN
        peer.on(SKWPeerEventEnum.PEER_EVENT_OPEN,callback:{ (obj) -> Void in
            if let peerId = obj as? String{
                DispatchQueue.main.async {
                    //self.myPeerIdLabel.text = peerId
                    //self.myPeerIdLabel.textColor = UIColor.darkGray
                }
                print("your peerId: \(peerId)")
            }
        })
        
        // MARK: PEER_EVENT_CONNECTION
        peer.on(SKWPeerEventEnum.PEER_EVENT_CALL, callback: { (obj) -> Void in
            if let connection = obj as? SKWMediaConnection{
                self.setupMediaConnectionCallbacks(mediaConnection: connection)
                self.mediaConnection = connection
                //connection.answer(self.localStream)
            }
        })
    }
    
    func setupMediaConnectionCallbacks(mediaConnection:SKWMediaConnection){
        
        // MARK: MEDIACONNECTION_EVENT_STREAM
        mediaConnection.on(SKWMediaConnectionEventEnum.MEDIACONNECTION_EVENT_STREAM, callback: { (obj) -> Void in
            if let msStream = obj as? SKWMediaStream{
                self.remoteStream = msStream
                DispatchQueue.main.async {
                    //self.targetPeerIdLabel.text = self.remoteStream?.peerId
                    //self.targetPeerIdLabel.textColor = UIColor.darkGray
                    self.remoteStream?.addVideoRenderer(self.remoteStreamView, track: 0)
                }
                //self.changeConnectionStatusUI(connected: true)
            }
        })
        
        // MARK: MEDIACONNECTION_EVENT_CLOSE
        mediaConnection.on(SKWMediaConnectionEventEnum.MEDIACONNECTION_EVENT_CLOSE, callback: { (obj) -> Void in
            if let _ = obj as? SKWMediaConnection{
                DispatchQueue.main.async {
                    self.remoteStream?.removeVideoRenderer(self.remoteStreamView, track: 0)
                    self.remoteStream = nil
                    self.mediaConnection = nil
                }
                //self.changeConnectionStatusUI(connected: false)
            }
        })
    }
}
