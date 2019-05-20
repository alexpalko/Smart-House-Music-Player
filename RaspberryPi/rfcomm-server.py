from bluetooth import *
import threading
import json
import select
import os
import subprocess
import firebase_admin
from firebase_admin import credentials, firestore
import traceback
import RPi.GPIO as GPIO
import time
import signal
import vlc

UNIQUE_ID = u'yfh2BrEulRZRkJOINEM4'
cred = credentials.Certificate('/home/pi/SHMP/ServiceAccountKey.json')
fireapp = firebase_admin.initialize_app(cred)
db = firestore.client()

user_ref = None

server_sock = None
client_sock = None
crt_json = None
device_disconnected = True

class Room:
    def __init__(self, pin, room_number):
        self.pin = pin
        self.room_number = room_number
        self.room_id = None
        self.urls = None
        self.proc = None
        self.playlist_id = None
        print ("Room %d created" % room_number)

rooms = []
k = 6

def initialize_rooms(number_of_rooms):
    global rooms
    global k
    for i in range(0, number_of_rooms):
        k = k+2
        print k
        GPIO.setup(k, GPIO.IN)
        rooms.append(Room(k, i))
        
def initialize_proc():
    global rooms
    print "Initializing audio processes"
    
    for i in range (0, len(rooms)):
        process = subprocess.Popen(['python', '/home/pi/SHMP/audio-player.py'] + rooms[i].urls)
        rooms[i].proc = process
        #rooms[i].proc.send_signal(signal.SIGUSR1)
        os.system('sudo kill -SIGSTOP %d' % rooms[i].proc.pid)
        print ("Initialized process with pid %d" % rooms[i].proc.pid)        
    print "All audio processes initialized"
    
def newAudio(index):
    global rooms
    print "Changing audio"
    
    if len(rooms) <= index:
        print 'Error: number of seonsors is greater than number of rooms'
    else:
        for i in range(0, len(rooms)):
            if i == index:
                os.system('mixer cset numid=3 %d >/dev/null 2>&1' % i)
                ##rooms[i].proc.send_signal(signal.SIGUSR2)
                os.system('sudo kill -SIGCONT %d' % rooms[i].proc.pid)
            else:
                #rooms[i].proc.send_signal(signal.SIGUSR1)
                os.system('sudo kill -SIGSTOP %d' % rooms[i].proc.pid)

def initServer():
    global server_sock
    # Change path to bluetooth_adv according to where it is located on your device
    os.system('sudo /home/pi/SHMP/bluetooth_adv')
    os.system('sudo hciconfig hci0 piscan')
    server_sock=BluetoothSocket( RFCOMM )
    server_sock.bind(("",PORT_ANY))
    server_sock.listen(1)
    port = server_sock.getsockname()[1]

    uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"

    advertise_service( server_sock, "SampleServer",
                       service_id = uuid,
                       service_classes = [ uuid, SERIAL_PORT_CLASS ],
                       profiles = [ SERIAL_PORT_PROFILE ],
                        )
                       
    print("Waiting for connection on RFCOMM channel %d" % port)

def pairWithDevice():
    global server_sock
    global client_sock
    global device_disconnected
    print "Ready to pair"
    client_sock, client_info = server_sock.accept()
    client_sock.setblocking(0)
    print("Accepted connection from ", client_info)
    device_disconnected = False
    
def receiveCommands():
    global server_sock
    global client_sock
    global crt_json
    global device_disconnected
    unprocessed_jsons = []
    unfinished_json = ""
    
    try:
        while True:
            ready_to_receive = select.select([client_sock], [], [], 0.1)
            if ready_to_receive[0]:
                data = client_sock.recv(1024)
                receivedPackets = data.split(";")
                for x in receivedPackets:
                    if x:
                        try:
                            recv_json = json.loads(x)
                            unprocessed_jsons.append(recv_json)
                        except ValueError:
                            unfinished_json += x
                            if unfinished_json: 
                                try:
                                    recv_json = json.loads(unfinished_json)
                                    unprocessed_jsons.append(recv_json)
                                except ValueError:
                                    pass
                                unfinished_json = ""
                print("received [%s]" % data)
            if unprocessed_jsons and not crt_json:
                crt_json = unprocessed_jsons.pop(0)
    except IOError:
        pass

    print("Device disconnected")
    device_disconnected = True

def sendJson(json_to_send):
    global client_sock
    try:
        client_sock.send(json_to_send)
    except ValueError:
        print "Error sending json: " + json_to_send
    print "Sent json: " + json_to_send

def checkRegistrationNeeded():
    global db
    global UNIQUE_ID
    
    rpis_ref = db.collection(u'rpis')
    docs = rpis_ref.get()
    for doc in docs:
        if doc.id == UNIQUE_ID:
            doc_dict = doc.to_dict()
            if doc_dict[u'userId']:
                sendJson('{ "response" : "nok" }')
                print "Registration not required"
                return False
    sendJson('{ "response" : "ok" }') 
    print "Ready to register"
    return True

def usernameTaken(username):
    users_ref = db.collection(u'users')
    docs = users_ref.get()
    for doc in docs:
        doc_dict = doc.to_dict()
        if u'username' in doc_dict:
            if doc_dict[u'username'] == username:
                return True
    return False

def registerAccount():
    global crt_json
    global UNIQUE_ID
    global user_ref
    
    print "Waiting for new user data"
    while not crt_json:
        if device_disconnected:
            return
    if 'username' in crt_json and 'password' in crt_json and 'email' in crt_json:
        if usernameTaken(crt_json['username']):
            sendJson('{ "response" : "user_taken" }')
            crt_json = None
            registerAccount()
        else:
            try:
                user_ref = db.collection(u'users').document()
                user_ref.set({
                    u'email' : crt_json['email'],
                    u'password' : crt_json['password'],
                    u'username' : crt_json['username']
                })
                
                print "User registered"
                
                playlist_ref = db.collection(u'playlists').document()
                playlist_ref.set({
                    u'name' : u'All songs',
                    u'userId' : user_ref.id
                })
                
                print "Default playlist created"
                
                user_ref.update({
                    u'playlistId' : playlist_ref.id                   
                })
                
                print "Default playlist linked to new user"
                
                rpi_ref = db.collection(u'rpis').document(UNIQUE_ID)
                rpi_ref.update({
                    u'userId' : user_ref.id                    
                })
                
                print "RPi owner updated"
                
                sendJson(u'{ "response" : "ok" }')
            except Exception:
                sendJson(u'{ "response" : "nok" }')
                crt_json = None
                registerAccount()
    else:        
        sendJson(u'{ "response" : "nok" }')
        crt_json = None
        registerAccount()

def getUserId():
    global UNIQUE_ID    
    rpi_ref = db.collection(u'rpis').document(UNIQUE_ID)
    rpi_data = rpi_ref.get().to_dict()
    return rpi_data[u'userId']

def login():
    global crt_json
    global user_ref
    
    print 'Logging in'
    
    if u'username' in crt_json and u'password' in crt_json:
        user_ref = db.collection(u'users').document(getUserId())
        user_data = user_ref.get().to_dict()
        if user_data[u'username'] != crt_json[u'username'] or user_data[u'password'] != crt_json[u'password']:
            sendJson('{ "response" : "nok" }')
            print "Login failed, invalid credentials"
        else:
            sendJson('{ "response" : "ok" }')

def getOwnedSongsIds():
    global user_ref
    global db
    print "Getting owned songs ids"
    lookup_docs = db.collection(u'playlistSongLookup').get()
    default_playlist_id = user_ref.get().to_dict()[u'playlistId']
    owned_songs = []
    for doc in lookup_docs:
        doc_data = doc.to_dict()
        if doc_data[u'playlistId'] == default_playlist_id:
            owned_songs.append(doc.to_dict()[u'songId'])
    print "Owned songs ids retrieved"
    return owned_songs

def getSongsForStore():
    global crt_json
    global user_ref
    global db
    owned_songs_ids = getOwnedSongsIds()
    song_docs = db.collection(u'songs').get()
    print "Getting song titles and ids"
    song_titles = []
    song_ids = []
    for doc in song_docs:
        if doc.id not in owned_songs_ids:
            song_titles.append(doc.to_dict()[u'title'])
            song_ids.append(doc.id)
    print "Songs titles and ids retrieved"
    sendJson ('{ "response" : "ok", "titles" : [' + ", ".join(song_titles) + '] }')
    
    while True:
        if crt_json is None:
            continue
        if u'command' in crt_json:
            if crt_json[u'command'] == u'exit':
                crt_json = None
            break
        if u'title' in crt_json:
            for i in range (0, len(song_titles)):
                if song_titles[i] == crt_json[u'title']:
                    db.collection(u'playlistSongLookup').document().set({
                        u'playlistId' : user_ref.get().to_dict()[u'playlistId'],
                        u'songId' : song_ids[i]
                    })
                    print song_titles[i] + " added to default playlist"
                    break
        crt_json = None
    print "Store closed"
                  
def getPlaylistIdByName(name):
    global db
    global user_ref
    playlist_docs = db.collection(u'playlists').get()
    
    for doc in playlist_docs:
        doc_data = doc.to_dict()
        if doc_data[u'name'] == name and doc_data[u'userId'] == user_ref.id:
            return doc.id
        
def getRoomNames():
    global crt_json
    global UNIQUE_ID
    global db
    print "Getting room nanmes"
    room_docs = db.collection(u'rooms').get()
    
    names = []
    
    for doc in room_docs:
        doc_data = doc.to_dict()
        if doc_data[u'rpiId'] == UNIQUE_ID:
            names.append(doc_data[u'name'])
    print "Room names retrieved"
    sendJson ('{ "response" : "ok", "names" : [' + ", ".join(names) + '] }')
                  
def getPlaylistNames():
    global crt_json
    global UNIQUE_ID
    global db
    print "Getting playlist names"
    room_docs = db.collection('rooms').get()
    
    names = []
    
    for doc in room_docs:
        doc_data = doc.to_dict()
        if doc_data[u'rpiId'] == UNIQUE_ID:
            names.append(db.collection(u'playlists').document(doc_data[u'playlistId']).get().to_dict()[u'name'])
    print "Playlist names retrieved"
    sendJson ('{ "response" : "ok", "names" : [' + ", ".join(names) + '] }')
                  
def setRoomsPlaylists():
    global crt_json
    global db
    global user_ref
    global UNIQUE_ID
    print "Setting rooms playlists"
    iterator = crt_json.iteritems()
    next(iteritems)
    
    for room_name, playlist_name in crt_json.iteritems():
        rooms_docs = db.collection(u'rooms').get()
        for doc in room_docs:
            doc_data = doc.to_dict()
            if doc_data[u'rpiId'] == UNIQUE_ID and doc_data[u'name'] == roomName:
                playlist_id = playlist_name
                if doc_data[u'playlistId'] != playlist_id:
                    doc.update({
                        u'playlistId' : playlist_id
                    })
                    print "Playlist for " + room_name + " changed"
                    # MODIFY AUDIO                  

def initializePlaylist(room):
    global db
    global rooms
    if room.playlist_id:
        return
    print "Gettig urls"
    playlist_id = db.collection(u'rooms').document(room.room_id).get().to_dict()[u'playlistId']
    room.playlist_id = playlist_id
    room.urls = []
    lookup = db.collection(u'playlistSongLookup').get()
    
    for doc in lookup:
        doc_data = doc.to_dict()
        if doc_data[u'playlistId'] == playlist_id:
            room.urls.append(db.collection(u'songs').document(doc_data[u'songId']).get().to_dict()[u'url'])
    print "Urls retrieved"
    for rm in rooms:
        if rm.playlist_id == playlist_id:
            rm.urls = room.urls
    print "Urls set"

def initializeRoomIds():
    global db
    global UNIQUE_KEY
    global rooms
    print "Initializing room ids"
    room_docs = db.collection(u'rooms').get()
    for doc in room_docs:
        doc_data = doc.to_dict()
        for room in rooms:
            if room.pin == doc_data[u'pin']:
                room.room_id = doc.id
                room.room_playlist_id = doc_data[u'playlistId']
    print "Room ids initialized"
    
if __name__ == '__main__':
    audio_paused = True
    
    initServer()
    GPIO.setmode(GPIO.BOARD)
    
    initialize_rooms(3)
    initializeRoomIds()
    for i in range(0, len(rooms)):
        initializePlaylist(rooms[i])

    initialize_proc()
    #state of the sensors
    state = []

    #state of the speakers
    speakers = [ False, False, False]
    
    for room in rooms:
        state.append(GPIO.input(room.pin))
    
    pairWithDevice()
    recvCommandsThread = threading.Thread(target = receiveCommands)
    recvCommandsThread.start()
    
    counter = 0
    try:
        while True:
            if device_disconnected:
                for room in rooms:
                    room.proc.send_signal(signal.SIGTERM)
                recvCommandsThread.join()
                if client_sock:
                    client_sock.close()
                    client_sock = None
                os.system('sudo hciconfig hci0 piscan')
                pairWithDevice()
                recvCommandsThread = threading.Thread(target = receiveCommands)
                recvCommandsThread.start()
                counter = 0
            else:
                #get the state of the sensors
                r = 0
                crt_reading = [0, 0, 0]
                for room in rooms:
                    crt_reading[r] = GPIO.input(room.pin)
                    state[r] += crt_reading[r]
                    r = r + 1
                counter += 1
            #print ("Sensor states: %d%d%d" % (crt_reading[0], crt_reading[1], crt_reading[2]))
            if (counter == 20):
                if state[0]/counter <= 0.7 and speakers[0] == False:
                    speakers[0] = True
                    speakers[1] = False
                    speakers[2] = False
                    #rooms[1].proc.send_signal(signal.SIGUSR1)
                    #rooms[2].proc.send_signal(signal.SIGUSR1)
                    print "Presence in Room 1"
                    if not audio_paused:
                        newAudio(0)
                elif state[1]/counter <= 0.7 and speakers[1] == False:
                    speakers[0] = False
                    speakers[1] = True
                    speakers[2] = False
                    #rooms[0].proc.send_signal(signal.SIGUSR1)
                    #rooms[2].proc.send_signal(signal.SIGUSR1)
                    print "Presence in Room 2"
                    if not audio_paused:
                        newAudio(1)
                elif state[2]/counter <= 0.7 and speakers[2] == False:
                    speakers[0] = False
                    speakers[1] = False
                    speakers[2] = True
                    #rooms[0].proc.send_signal(signal.SIGUSR1)
                    #rooms[1].proc.send_signal(signal.SIGUSR1)
                    print "Presence in room 3"
                    if not audio_paused:
                        newAudio(2)
                state[0] = 0
                state[1] = 0
                state[2] = 0
                counter = 0
                #time.sleep(1)
                
            if crt_json:
                print "Processing json: " + json.dumps(crt_json)
                if 'command' in crt_json:
                    cmd = crt_json["command"]
                    if cmd == "register":
                        if checkRegistrationNeeded():
                            crt_json = None
                            registerAccount()
                    elif cmd == "login":
                        login()
                    elif cmd == "get_songs_store":
                        crt_json = None
                        getSongsForStore()
                    elif cmd == "get_room_names":
                        crt_json = None
                        getRoomNames()
                    elif cmd == "get_room_playlist_names":
                        crt_json = None
                        getPlaylistNames()
                    elif cmd == "play":
                        print audio_paused
                        for i in range(0, len(rooms)):
                            if speakers[i] == True:
                                os.system('mixer cset numid=3 %d >/dev/null 2>&1' % i)
                                os.system('sudo kill -SIGCONT %d' % rooms[i].proc.pid)
                                break
                        audio_paused = False                        
                        print "Now playing"
                        print audio_paused
                    elif cmd == "pause":
                        audio_paused = True
                        for room in rooms:
                            print ("Stopping process %d" % room.proc.pid)
                            #room.proc.send_signal(signal.SIGSTOP)
                            os.system('sudo kill -SIGSTOP %d' % room.proc.pid)
                        print "Now paused"
                crt_json = None
    except Exception:            
        server_sock.close()
        traceback.print_exc()

