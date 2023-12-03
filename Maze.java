/*********************************************************************************

   Creates a random maze, then solves it by finding a path from the
   upper left corner to the lower right corner.  (After doing
   one maze, it waits a while then starts over by creating a
   new random maze.)
   
        name            default value    meaning
       ------           ---------------  --------------------------------------
        rows                     21         number of rows in maze (must be odd)
        columns                  21         number of columns in maze (must be odd)
        border                    0         width of colored border around maze
                                              (extra space after making equal-sized
                                               rows and columns is also part of the border)
        sleepTime              1000         pause, in milliseconds between
                                               solving one maze and creating
                                               another
        wallColor             black  
        emptyColor      128 128 255
        pathColor           200 0 0      
        visitedColor  same as empty
        borderColor           white
        
Original author:  David Eck
     Department of Mathematics and Computer Science
     Hobart and William Smith Colleges
     Geneva, NY   14456

NOTE:  YOU CAN DO ANYTHING YOU WANT WITH THIS CODE AND APPLET, EXCEPT
       CLAIM CREDIT FOR THEM (such as by trying to copyright the code
       your self).

MODIFIED: 2/28/07 by S. Anderson
          9/13/16 Converted from applet to application.

**************************************************************************/

/**
 * Lab 4: Stacks and Mazes
 * @author Damian Dobrowolski
 * @date 10.08.2023 (received extension)
 * */


import java.awt.*;
import javax.swing.*;
import java.net.URL;
import javax.sound.sampled.*;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Stack;


public class Maze extends JFrame implements Runnable {


    /* Description of state of maze.  The value of maze[i][j] is one
     of the constants wallCode, pathcode, EMPTYCODE, or visitedCode.
     (Value can also be negative, temporarily, inside createMaze().)
     A maze is made up of walls and corridors.  maze[i][j] is either
     part of a wall or part of a corridor.  A cell cell that is part
     of a cooridor is represented by pathCode if it is part of the
     current path through the maze, by visitedCode if it has already
     been explored without finding a solution, and by EMPTYCODE if it
     has not yet been explored.
    */
    private int[][] maze;
    public final static int BACKGROUNDCODE = 0;
    public final static int WALLCODE = 1;
    public final static int PATHCODE = 2;
    public final static int EMPTYCODE = 3;
    public final static int VISITEDCODE = 4;
    public final static int SOLUTIONCODE = 5;

    public static final String FAILSOUND = "bell.wav"; // File for failure.
    
    Color[] color = new Color[6];  // colors associated with the preceding 6 constants;
    private int rows = 21;          // number of rows of cells in maze, including a wall around edges
    private int columns = 21;       // number of columns of cells in maze, including a wall around edges
    private int border = 20;         // minimum number of pixels between maze and edge of frame
    private int sleepTime = 1500;   // wait time after solving one maze before making another
    private int speedSleep = 30;    // short delay between steps in solving maze
    private int makeSleep = 1;    // short delay between steps in making maze
    
    private Thread mazeThread;   // thread for creating and solving maze
    private int width = -1;   // width of app
    private int height = -1;  // height of app
    private int cellWidth = 10; // width of each unit of the wall (or path)
    private int left;         // left edge of maze, allowing for border
    private int top;          // top edge of maze, allowing for border
    
    private boolean mazeExists = false;  // set to true when maze[][] is valid; used in
                                 // redrawMaze(); set to true in createMaze(), and
                                 // reset to false in run()

    private int status = 0;  // A variable used by the applet to control the mazeThread.

    private String soundfile = "bell.wav";
    
    public final static int GO = 0,         // Some constants to be uses as values for status.
    SUSPEND = 1,
    TERMINATE = 2;

    private DrawPane canvas; // JPanel where maze is drawn

    /**
       Construct Maze, an extension of JFrame.
    */
    public Maze(int nr, int nc) {
     rows = columns = 11;
     if (nr > 0 && nr < 100)
      rows = nr;
  if (nc > 0 && nc < 100)
      columns = nc;
	if (rows % 2 == 0) rows++; // must be odd
	if (columns % 2 == 0) columns++;
	color[WALLCODE] = Color.black;
	color[PATHCODE] = new Color(200,0,0);
	color[EMPTYCODE] = new Color(128,128,255);
	color[BACKGROUNDCODE] = Color.black;
	color[VISITEDCODE] = color[EMPTYCODE];
	color[SOLUTIONCODE] = new Color(0,255,255);
	setBackground(color[BACKGROUNDCODE]);

	width = columns * cellWidth + 2 * border;
	height = rows * cellWidth + 2 * border;
	left = border;         // left edge of maze, allowing for border
	top = border;          // top edge of maze, allowing for border

	canvas = new DrawPane();
	canvas.setSize(width,height);
	setContentPane(canvas);

	setSize(width,height);
	setVisible(true);
}

    /**
       Start running the thread.
    */
synchronized public void start() {
    status = GO;
    if (mazeThread == null || ! mazeThread.isAlive()) {
      mazeThread = new Thread(this);
      mazeThread.start();
  }
  else
   notify();
}

synchronized public void stop() {
    if (mazeThread != null) {
        status = SUSPEND;
        notify();
    }
}

synchronized public void destroy() {
   if (mazeThread != null) {
      status = TERMINATE;
      notify();
  }
}

synchronized int checkStatus() {
   while (status == SUSPEND) {
      try { wait(); }
      catch (InterruptedException e) { }
  }
  return status;
}


    /**
       Controls execution of main thread.
    */
public void run() {
	// run method for thread repeatedly makes a maze and then solves it
       try { Thread.sleep(sleepTime); } // wait a bit before starting
       catch (InterruptedException e) { }
       while (true) {
          if (checkStatus() == TERMINATE)
             break;
         makeMaze();
         if (checkStatus() == TERMINATE)
             break;
	  // Signal failure with a bell.
         if (solveMaze(new Position(1,1))) { 
           delay(sleepTime);
       } else {
           playSound(FAILSOUND);
           delay(sleepTime);
       }

       if (checkStatus() == TERMINATE)
         break;
     synchronized(this) {
       delay(speedSleep);

   }
   if (checkStatus() == TERMINATE)
     break;
 mazeExists = false;
          //this.repaint();  // erase old maze
}
}

    /**
     Create a random maze.  The strategy is to start with a grid of
     disconnnected "rooms" separated by walls.  then look at each of
     the separating walls, in a random order.  If tearing down a wall
     would not create a loop in the maze, then tear it down.
     Otherwise, leave it in place.

     Resulting maze has an uninterrupted wall around it.
    */
void makeMaze() {
    if (maze == null)
       maze = new int[rows][columns];
   int i,j;
        int emptyCt = 0; // number of rooms
        int wallCt = 0;  // number of walls
        int[] wallrow = new int[(rows*columns)/2];  // position of walls between rooms
        int[] wallcol = new int[(rows*columns)/2];
        for (i = 0; i<rows; i++)  // start with everything being a wall
            for (j = 0; j < columns; j++)
                maze[i][j] = WALLCODE;
        for (i = 1; i<rows-1; i += 2)  // make a grid of empty rooms
            for (j = 1; j<columns-1; j += 2) {
                emptyCt++;
                maze[i][j] = -emptyCt;  // each room is represented by a different negative number
                if (i < rows-2) {  // record info about wall below this room
                    wallrow[wallCt] = i+1;
                    wallcol[wallCt] = j;
                    wallCt++;
                }
                if (j < columns-2) {  // record info about wall to right of this room
                    wallrow[wallCt] = i;
                    wallcol[wallCt] = j+1;
                    wallCt++;
                }
            }
            mazeExists = true;

            if (checkStatus() == TERMINATE)
               return;
        repaint();  // show the maze
        int r;
        for (i=wallCt-1; i>0; i--) {
            r = (int)(Math.random() * i);  // choose a wall randomly and maybe tear it down
            if (checkStatus() == TERMINATE)
               return;
           tearDown(wallrow[r],wallcol[r]);
           wallrow[r] = wallrow[i];
           wallcol[r] = wallcol[i];
       }
        for (i=1; i<rows-1; i++)  // replace negative values in maze[][] with EMPTYCODE
           for (j=1; j<columns-1; j++)
              if (maze[i][j] < 0)
                  maze[i][j] = EMPTYCODE;

	// Add a few random walls.
              int nrandomwalls = 5;

        for (i=1; i<rows-1; i++) { // replace some corridors with a wall element
         for (j=1; j<columns-1; j++) {
          if (maze[i][j] == EMPTYCODE &&
            Math.random() < 0.01) {
            maze[i][j] = WALLCODE;
        }
    }
}

}



synchronized void tearDown(int row, int col) {
       // Tear down a wall, unless doing so will form a loop.  Tearing down a wall
       // joins two "rooms" into one "room".  (Rooms begin to look like corridors
       // as they grow.)  When a wall is torn down, the room codes on one side are
       // converted to match those on the other side, so all the cells in a room
       // have the same code.   Note that if the room codes on both sides of a
       // wall already have the same code, then tearing down that wall would 
       // create a loop, so the wall is left in place.
    if (row % 2 == 1 && maze[row][col-1] != maze[row][col+1]) {
                       // row is odd; wall separates rooms horizontally
        fill(row, col-1, maze[row][col-1], maze[row][col+1]);
        maze[row][col] = maze[row][col+1];
    }
    else if (row % 2 == 0 && maze[row-1][col] != maze[row+1][col]) {
                      // row is even; wall separates rooms vertically
        fill(row-1, col, maze[row-1][col], maze[row+1][col]);
        maze[row][col] = maze[row+1][col];
    }
}

void fill(int row, int col, int replace, int replaceWith) {
	// called by tearDown() to change "room codes".
    if (maze[row][col] == replace) {
        maze[row][col] = replaceWith;
        fill(row+1,col,replace,replaceWith);
        fill(row-1,col,replace,replaceWith);
        fill(row,col+1,replace,replaceWith);
        fill(row,col-1,replace,replaceWith);
    }
}



    // Add delay to the program.
void delay(int msec) {
	synchronized(this) {
       try { wait(msec); }
       catch (InterruptedException e) { }
   }
}



public static void playSound(String filename) {
	try {
	    // Open an audio input stream.
	    //URL url = this.getClass().getClassLoader().getResource(filename);
     File file = new File(filename);
     AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);

     DataLine.Info info = new DataLine.Info(Clip.class, audioIn.getFormat());
	    // Get a sound clip resource.
     Clip clip = (Clip) AudioSystem.getLine(info);
	    //Clip clip = AudioSystem.getClip();
	    // Open audio clip and load samples from the audio input stream.
     clip.open(audioIn);
     clip.start();
 } catch (UnsupportedAudioFileException e) {
     e.printStackTrace();
 } catch (IOException e) {
     e.printStackTrace();
 } catch (LineUnavailableException e) {
     e.printStackTrace();
 }
}


    // Component where maze is rendered.
private class DrawPane extends JPanel {
	/**
	   This method is called implicitly.  It simply draws mazes as
	   a collection of rectangles of various colors.
	*/
    public void paintComponent(Graphics g){
     g.setColor(color[BACKGROUNDCODE]);
     g.fillRect(0,0,width,height);
     if (mazeExists) {
		int w = cellWidth;  // width of each cell
		int h = cellWidth;    // height of each cell
		for (int j=0; j<columns; j++)
          for (int i=0; i<rows; i++) {
           if (maze[i][j] < 0)
               g.setColor(color[EMPTYCODE]);
           else
               g.setColor(color[maze[i][j]]);
           g.fillRect( (j * w) + left, (i * h) + top, w, h );
       }
   }
}


}

    /**
       @param here current position
       @return true if maze is solved.

      Try to solve the maze by continuing current path from position
      (row,col).  Return true if a solution is found.  The maze is
      considered to be solved if the path reaches the lower right
      cell.
    */
public boolean solveMaze(Position here){
	// TODO
    //Creates stack of Position objects
    Stack<Position> path;
    path = new Stack<Position>();
    //Adds origin position from parameter call in run() to stack
    path.push(here);
    //Colors initial cell as path
    maze[here.row][here.column] = PATHCODE;

    //Loops while maze is solvable
    while(true){
        //Sets cell to a move from top of Stack (which is most recent cell)
        Position nextCell = findMove(path.peek());

        //If no empty adjacent cells (findMove returns 0,0)
        if (nextCell.row == 0 && nextCell.column == 0){
            //Sets to visited
            maze[path.peek().row][path.peek().column] = VISITEDCODE;
            //Painting animation
            canvas.repaint();
            delay(50);
            //Backs up one cell
            path.pop();
        //If there are empty adjacent cells
        } else{
            //Marks as path
            maze[nextCell.row][nextCell.column] = PATHCODE;
            //Painting animation
            canvas.repaint();
            delay(50);
            //Adds cell to path
            path.push(nextCell);
        }

        //If maze is completed and cell is the end cell
        if (nextCell.row == (rows - 2) && nextCell.column == (columns - 2)){
            //Adds to path
            path.push(nextCell);
            //Resets path and colors solution
            /*while (!path.isEmpty()){
                Position solution = path.pop();
                maze[solution.row][solution.column] = SOLUTIONCODE;
            }
            */
            for (Position cell : path){
                maze[cell.row][cell.column] = SOLUTIONCODE;
            }
            //Painting animation
            canvas.repaint();
            //One-second delay
            delay(1000);
            //Maze solved so returns true
            return true;
        }

        //If maze is back at start because all paths are explored and no solution found
        if (path.peek().row == here.row && path.peek().column == here.column && nextCell.row == 0 && nextCell.column == 0){
            //Painting animation
            canvas.repaint();
            //One second delay
            delay(1000);
            //Maze is not solvable so set to false
            return false;
        }
    }
}

/**
       @param pos cell
       @return Position cell of either possible move or if none, (0,0)

      Tests adjacent cells for emptiness to return possible path.
      If there are no empty adjacent cells, returns (0,0).
*/

private Position findMove(Position pos){
    int r = pos.row;
    int c = pos.column;
    //Checks if cell above is empty
    if (maze[r-1][c] == EMPTYCODE){
        return new Position(r-1, c);
    //Checks is cell to right is empty
    } else if (maze[r][c+1] == EMPTYCODE){
        return new Position(r, c+1);
    //Checks is cell below is empty
    } else if (maze[r+1][c] == EMPTYCODE){
        return new Position(r+1, c);
    //Checks is cell to left is empty
    } else if (maze[r][c-1] == EMPTYCODE){
        return new Position(r, c-1);
    } else{
    //If no empty adjacent cells, returns (0,0)
        return new Position(0,0);
    }
}

    // Inner class to maintain row, col.
private class Position {
	// TODO
    private int row;
    private int column;
    //Default constructor itializes Position to 0,0
    public Position(){
        row = 0;
        column = 0;
    }
    /** Constructor to set row and column to parameter input
     * @param row
     * @param column
     * */
     public Position(int r, int c) {
	    // TODO
        row = r;
        column = c;
    }
}

public static void main(String[] args) {
	Maze maze = new Maze(21,21);
	playSound(Maze.FAILSOUND);
	maze.start();
	maze.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
}
}
