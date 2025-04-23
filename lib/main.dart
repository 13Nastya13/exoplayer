import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Video Streaming App',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'ExoPlayer Streaming Demo'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  var channel = const MethodChannel('native_player');
  String? _lastPlayedVideo;
  String? _playbackStatus;
  
  // Sample streaming URLs
  final Map<String, String> streamingVideos = {
    'Big Buck Bunny': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    'Elephants Dream': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
    'Sintel': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
    'HLS Stream': 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8',
    'DASH Stream': 'https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd'
  };

  Future<void> callNativePlayer({String? videoUrl, String? videoTitle}) async {
    try {
      final videoTitleValue = videoTitle ?? 'Big Buck Bunny';
      final result = await channel.invokeMethod('open_player', {
        'video_url': videoUrl ?? streamingVideos['Big Buck Bunny'],
        'video_title': videoTitleValue
      });
      
      // Handle result from native player
      if (result != null && result is Map) {
        setState(() {
          _lastPlayedVideo = videoTitleValue;
          
          if (result['success'] == true) {
            final position = result['position'] as int?;
            final completed = result['completed'] as bool?;
            
            final positionInSeconds = position != null ? (position / 1000).round() : 0;
            
            if (completed == true) {
              _playbackStatus = 'Video completed';
            } else {
              _playbackStatus = 'Stopped at ${positionInSeconds}s';
            }
          } else {
            _playbackStatus = 'Playback failed';
          }
        });
      }
    } catch (e) {
      print('Error calling native player: $e');
      setState(() {
        _playbackStatus = 'Error: $e';
      });
    }
  }

  int _counter = 0;

  void _incrementCounter() {
    setState(() {
      _counter++;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            const Text(
              'Select a streaming video to play:',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            if (_lastPlayedVideo != null && _playbackStatus != null)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Card(
                  color: Colors.grey[200],
                  child: Padding(
                    padding: const EdgeInsets.all(12.0),
                    child: Column(
                      children: [
                        Text(
                          'Last played: $_lastPlayedVideo',
                          style: TextStyle(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 4),
                        Text(_playbackStatus!)
                      ],
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 12),
            Expanded(
              child: ListView.builder(
                itemCount: streamingVideos.length,
                itemBuilder: (context, index) {
                  String title = streamingVideos.keys.elementAt(index);
                  String url = streamingVideos.values.elementAt(index);
                  return Card(
                    elevation: 2,
                    margin: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    child: ListTile(
                      title: Text(title),
                      subtitle: Text(url.length > 50 ? '${url.substring(0, 50)}...' : url),
                      onTap: () => callNativePlayer(
                        videoUrl: url,
                        videoTitle: title
                      ),
                      trailing: Icon(Icons.play_circle_filled, color: Colors.deepPurple),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
