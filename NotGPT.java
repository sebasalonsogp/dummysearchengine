package prog11;

import prog05.ArrayQueue;

import java.util.*;

public class NotGPT implements SearchEngine{

    public class PageComparator implements Comparator<Long>
    {
        @Override
        public int compare(Long pageIndex1, Long pageIndex2)
        {
            InfoFile page1 = pageDisk.get(pageIndex1);
            InfoFile page2 = pageDisk.get(pageIndex2);

            if (page1.priority > page2.priority)
            {
                return 1;
            } else if (page1.priority < page2.priority)
            {
                return -1;
            } else {
                return 0;
            }
        }
    }

    public class Vote implements Comparable<Vote>
    {

        Long index;
        double vote;

        Vote(Long index, Double vote)
        {
            this.index=index;
            this.vote=vote;

        }

        @Override
        public int compareTo(Vote o) {
            return index.compareTo(o.index);
        }
    }



    Disk wordDisk = new Disk();
    Map<String, Long> word2index = new HashMap<>();


    //PART 7
    Disk pageDisk = new Disk();
    Map<String,Long> url2index = new TreeMap<>();


    public long indexPage(String url){

        long index = pageDisk.newFile();
        InfoFile file = new InfoFile(url);

        pageDisk.put(index, file) ;
        url2index.put(url, index);


        System.out.println("Indexing " +  index + " " + file);
        return index;
    }

    public long indexWord(String word) // PART 8 -  I THINK THIS IS CORRECT BUT COULD POTENTIALLY BE WRONG.
    {
        Long index = wordDisk.newFile();
        InfoFile file = new InfoFile(word);

        wordDisk.put(index, file);
        word2index.put(word,index);

        System.out.println("Indexing word " + index +  " " + file );

        return index;
    }



    @Override
    public void collect(Browser browser, List<String> startingURLs)
    {

            Queue<Long> pageIndices = new ArrayQueue<Long>();

            for(String s : startingURLs)
            {
                if(!url2index.containsKey(s))
                {
                    Long index = indexPage(s);
                    pageIndices.offer(index);
                }
            }

            while(!pageIndices.isEmpty())
            {
               long index = pageIndices.poll();

               InfoFile url = pageDisk.get(index);


                if(browser.loadPage(url.data))
                {
                    Set<String> seen = new HashSet<String>();

                    Set<String> wordSeen= new HashSet<String>();

                    for (String url2 : browser.getURLs())
                    {
                        if (url2index.get(url2) == null)
                        {
                            //System.out.println("Sorry there is no page with that index!")
                            pageIndices.offer(indexPage(url2));
                        }
                        if(!seen.contains(url2)) // PART 6
                        {
                            seen.add(url2);
                            url.indices.add(url2index.get(url2));

                        } // PART 6
                    } //FOR EACH URL IN PAGE

                    for(String word : browser.getWords()) /// PART 9 - I AM WORKING ON THIS ONE ATM.
                    {
                        Long wordIndex = word2index.get(word);

                        if(wordIndex == null)
                        {
                            wordIndex = indexWord(word);
                        }
                        if(!wordSeen.contains(word))
                        {
                            wordSeen.add(word);
                           InfoFile words = wordDisk.get(wordIndex);
                           words.indices.add(index);

                        }

                    } // FOR EACH WORD IN PAGE

                } // IF BROWSER

            } // WHILE QUEUE IS NOT EMPTY


    }

    void rankSlow(double defaultPriority)
    {
        for(InfoFile file :pageDisk.values()) //1st
        {

            double priorityPerIndex = file.priority/(file.indices.size());

            for(Long i : file.indices)
            {
              pageDisk.get(i).tempPriority += priorityPerIndex;
            }
        }

        for (InfoFile file : pageDisk.values()) //2nd
        {

            file.priority= file.tempPriority+ defaultPriority;
            file.tempPriority=0;

        }




    }

    void rankFast(double defaultPriority) //part6 TODO
    {
        List<Vote> votes = new ArrayList<Vote>();
        for(InfoFile file : pageDisk.values())
        {

            for(Long i : file.indices)
            {

                votes.add(new Vote(i, file.priority/file.indices.size()));
            }


        }

        Collections.sort(votes);

        Iterator<Vote> iterator = votes.iterator();
        Vote vote = null;
        if(iterator.hasNext()){
            vote = iterator.next();
        }

        for(Map.Entry<Long, InfoFile> entry: pageDisk.entrySet()) //FOR EACH PAGE, add  priority of votes with index of page
        {
            long index = entry.getKey();
            InfoFile file = entry.getValue();

            file.priority=defaultPriority;

            while(vote != null && vote.index == index)
            {
                file.priority+=vote.vote;

                if(iterator.hasNext()){
                    vote = iterator.next();
                }
                else vote = null;


            }



        }


        } //end rankFast() method


    @Override
    public void rank(boolean fast) {

        int cnt=0;
        for(InfoFile file : pageDisk.values()){
            if(file.indices.isEmpty())
            {
                cnt++;
            }
        }

        double defaultPriority = 1.0 *cnt/pageDisk.size();

        for(Map.Entry<Long,InfoFile> entry : pageDisk.entrySet())
        {
            long index=entry.getKey();
            InfoFile file = entry.getValue();

            file.priority=1.0;
            file.tempPriority =0.0;

        }

        if(!fast){
            int i=0;
            while(i<20)
            {
                rankSlow(defaultPriority);
                i++;
            }

        }
        else{
            int i=0;
            while(i<20)
            {
                rankFast(defaultPriority);
                i++;
            }
        }

    }

    @Override
    public String[] search(List<String> searchWords, int numResults) {

        PriorityQueue<Long> bestPageIndexes = new PriorityQueue<>(pageDisk.size(), new PageComparator());

        Iterator<Long>[] wordFileIterators =
                (Iterator<Long>[]) new Iterator[searchWords.size()];

        long[] currentPageIndexes  = new long[searchWords.size()];




        for(int i=0;i<searchWords.size();i++)
        {
            String word = searchWords.get(i);
            Long index = word2index.get(word);
            InfoFile file = wordDisk.get(index);
            Iterator<Long> var = file.indices.iterator();

            wordFileIterators[i] = var;

        }


        while(getNextPageIndexes(currentPageIndexes,wordFileIterators))
        {
            if(allEqual(currentPageIndexes))
            {
                InfoFile file = pageDisk.get(currentPageIndexes[0]);
                Long index = currentPageIndexes[0];

                System.out.println("URL: " + file.data);


                if (bestPageIndexes.size() != numResults)
                {
                    bestPageIndexes.offer(currentPageIndexes[0]);
                } else
                {
                    InfoFile existingPage = pageDisk.get(bestPageIndexes.peek());
                    if ( file.priority>existingPage.priority)
                    {
                        bestPageIndexes.poll();
                        bestPageIndexes.offer(index);
                    }
                }



            }

        }
        String[] results = new String[numResults];
        int i=numResults-1;
        while (!bestPageIndexes.isEmpty())
        {
            Long pageIndex = bestPageIndexes.poll();
            InfoFile file2 = pageDisk.get(pageIndex);
            results[i] = file2.data;
            i--;
        }

        return results;
    }

    /** Check if all elements in an array of long are equal.
     @param array an array of numbers
     @return true if all are equal, false otherwise
     */
    private boolean allEqual (long[] array)
    {


        for(int i=0;i<array.length;i++)
        {
            if(array[i] != array[0])
                return false;

        }



        return true;
    }

        /** Get the largest element of an array of long.
         @param array an array of numbers
         @return largest element
         */
        private long getLargest (long[] array){

            Long high =array[0];
            for(int i=1;i<array.length;i++)
            {
                if(array[i] >  high)
                    high = array[i];

            }

            return high;
        }


    /** If all the elements of currentPageIndexes are equal,
     set each one to the next() of its Iterator,
     but if any Iterator hasNext() is false, just return false.

     Otherwise, do that for every element not equal to the largest element.

     Return true.

     @param currentPageIndexes array of current page indexes
     @param wordFileIterators array of iterators with next page indexes
     @return true if all page indexes are updated, false otherwise
     */
    private boolean getNextPageIndexes
    (long[] currentPageIndexes, Iterator<Long>[] wordFileIterators)
    {
        if(allEqual(currentPageIndexes))
        {
            for(int i =0;i<currentPageIndexes.length;i++)
            {
                if(!wordFileIterators[i].hasNext())
                {
                    return false;
                }
                currentPageIndexes[i] =  wordFileIterators[i].next();
            }
        }
        else
        {
            for(int i =0;i<currentPageIndexes.length;i++){
                if(getLargest(currentPageIndexes) != currentPageIndexes[i])
                {
                    if(!wordFileIterators[i].hasNext())
                    {
                        return false;
                    }
                    currentPageIndexes[i]=wordFileIterators[i].next();

                }
            }
        }

        return true;
    }


}
