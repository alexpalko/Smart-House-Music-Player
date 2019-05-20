import io
import requests
import os
import sys
import vlc
import signal


if __name__ == '__main__':
    for i in range(1, len(sys.argv)):
        player = vlc.MediaPlayer(sys.argv[i])
        player.play()
        while player.get_state() != vlc.State.Ended :
            pass

