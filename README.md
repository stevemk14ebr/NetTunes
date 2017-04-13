# NetTunes
Media synchronization client/server in java

Provides a client and server that allows remote playback of any media file, and keeps the playback of that media synced for all devices. 

Written entirely in java
## Dependencies
* Google guava
* VLCj: https://github.com/caprica/vlcj
* stateful (slightly modified): https://github.com/zevada/stateful

## Issues
This has slight synchonization issues due to the delay of sending the packets themselves over the network (< 100ms). The state-machine used also doesn't allow media to be played at the same time chat messages are sent. This limitation was an oversight in the choice of statemachine, my long term goal was to create a 'multi-event' statemachine that would allow synchonous states but well that sort of defeats the purpose of a statemachine doesn't it? It's a POC, for all intents and purposes it works, but needs revision for any serious use.

## LICENSE
GNU GPL 3.0
